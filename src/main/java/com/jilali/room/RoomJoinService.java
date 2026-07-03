package com.jilali.room;

import com.jilali.client.JilaliClient;
import com.jilali.client.JilaliResponses;
import com.jilali.comment.dto.CommentListResponse;
import com.jilali.core.JilaliProperties;
import com.jilali.room.dto.JoinBundleResponse;
import com.jilali.room.dto.VoiceRoomInfoResponse;
import com.jilali.stage.dto.StageListResponse;
import com.jilali.user.dto.RoomUserListRequest;
import com.jilali.user.dto.RoomUserListResponse;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

/**
 * Bundled join data for a room — fans four upstream LiveHub calls (room info, stage roster,
 * audience roster, comment history) out concurrently using Structured Concurrency on virtual
 * threads (Java 21+ preview, finalized in Java 25). This is what backs the BFF's
 * {@code GET /api/rooms/{cname}/join-bundle} endpoint ({@code RoomController.joinBundle}) — the
 * single call the frontend's room-join flow, "refresh room" button, and invisible→visible
 * toggle all use instead of hitting each of those four endpoints separately.
 *
 * <p>Each {@link StructuredTaskScope#fork} launches a virtual thread that blocks cheaply on I/O.
 * When {@link StructuredTaskScope#join} detects a failure, it automatically cancels the remaining
 * siblings — no explicit cancellation, no executor shutdown, no resource leaks.
 *
 * <p>The scope is closed in a try-with-resources, ensuring the carrier thread (if any) is always
 * unpinned even if an exception escapes.
 */
@Singleton
public class RoomJoinService {

    private static final Logger log = LoggerFactory.getLogger(RoomJoinService.class);

    private final JilaliClient client;
    private final JilaliProperties properties;

    public RoomJoinService(JilaliClient client, JilaliProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * Calls LiveHub's room info (voice or live, dispatched by {@code busiType}), stage list,
     * audience user list, and comment history concurrently.
     *
     * @param cname    room channel name
     * @param busiType business type (1 = live/video, 2 = voice — see voice-list/live-list route
     *                 dispatch on the frontend) — selects liveRoomInfo vs voiceRoomInfo
     * @return combined payload for a room join
     * @throws RuntimeException wrapping the upstream failure if any of the four calls errors
     */
    public JoinBundleResponse joinBundle(String cname, int busiType) {
        try (var scope = StructuredTaskScope.open()) {

            // Room metadata (voice or live — dispatched by busiType) including the RTC token,
            // needed by the frontend before it can open its websocket / audio connection.
            var voiceInfoTask = scope.fork(() -> JilaliResponses.unwrap(
                    busiType == 1 ? client.liveRoomInfo(cname) : client.voiceRoomInfo(cname)));
            // Who's currently on stage.
            var stageUsersTask = scope.fork(() -> JilaliResponses.unwrap(client.stageList(busiType, cname)));
            // The audience roster. get_type=[3] matches RoomApi.fetchAudienceUsers() on the
            // frontend — omitting it (null) would ask upstream for a different, undocumented
            // default roster shape.
            var audienceUsersTask = scope.fork(() -> JilaliResponses.unwrap(
                    client.roomUserList(new RoomUserListRequest(List.of(3), cname, busiType))));
            // Initial chat/comment history, so the room renders with its comment feed already
            // populated instead of the frontend needing a fifth call after the bundle resolves.
            var commentsTask = scope.fork(() -> JilaliResponses.unwrap(client.comments(busiType, cname)));

            // Throws StructuredTaskScope.FailedException if any subtask fails (others are cancelled).
            // Returns normally only when all four have completed successfully.
            scope.join();

            return new JoinBundleResponse(
                    decryptRtcToken(voiceInfoTask.get()),
                    stageUsersTask.get(),
                    audienceUsersTask.get(),
                    commentsTask.get()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during concurrent room join fetch", e);
        } catch (StructuredTaskScope.FailedException e) {
            throw new RuntimeException("Upstream fetch failed during room join", e.getCause());
        }
    }

    private VoiceRoomInfoResponse decryptRtcToken(VoiceRoomInfoResponse resp) {
        if (resp == null || resp.channelInfo() == null) {
            return resp;
        }
        if (resp.channelInfo().rtcInfo() == null) {
            log.warn("Upstream returned no RTC info for channel {}", resp);
            return resp;
        }
        var encrypted = resp.channelInfo().rtcInfo().token();
        byte[] key = properties.agoraCipherKey().getBytes(StandardCharsets.US_ASCII);
        return resp.withRtcToken(AgoraTokenCipher.decrypt(encrypted, key));
    }
}
