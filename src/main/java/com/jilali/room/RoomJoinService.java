package com.jilali.room;

import com.jilali.client.JilaliClient;
import com.jilali.client.JilaliResponses;
import com.jilali.comment.dto.Comment;
import com.jilali.comment.dto.CommentDto;
import com.jilali.comment.dto.CommentListDto;
import com.jilali.comment.dto.CommentListResponse;
import com.jilali.core.JilaliProperties;
import com.jilali.room.dto.JoinBundleResponse;
import com.jilali.room.dto.VoiceRoomInfoResponse;
import com.jilali.stage.dto.StageListResponse;
import com.jilali.user.dto.RoomUserListRequest;
import com.jilali.user.dto.RoomUserListResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;

/**
 * Bundled join data for a room — fetches LiveHub room info first, then fans the remaining
 * three upstream calls (stage roster, audience roster, comment history) out concurrently using
 * Structured Concurrency on virtual threads (Java 21+ preview, finalized in Java 25). This is
 * what backs the BFF's {@code GET /api/rooms/{cname}/join-bundle} endpoint
 * ({@code RoomController.joinBundle}) — the single call the frontend's room-join flow,
 * "refresh room" button, and invisible→visible toggle all use instead of hitting each of those
 * four endpoints separately.
 *
 * <p>Room info is sequenced before, not concurrent with, the other three — see
 * {@link #joinBundle}'s doc for why.
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

    // A room just created via create_voice_channel is occasionally not yet visible to
    // LiveHub's own read endpoints (stage/list, voice_room_info, user/list, comment) for a
    // brief window after creation — upstream returns a bare 500 for that cname until its own
    // indexing catches up. Retrying the *individual* failing call a few times, rather than
    // the whole bundle, resolves this without re-paying for the calls that already succeeded.
    // Never retries 4xx (a real "not found" / bad request should fail immediately).
    private static final int MAX_UPSTREAM_ATTEMPTS = 4;
    private static final Duration UPSTREAM_RETRY_DELAY = Duration.ofMillis(700);

    private final JilaliClient client;
    private final JilaliProperties properties;

    public RoomJoinService(JilaliClient client, JilaliProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * Calls LiveHub's room info (voice or live, dispatched by {@code busiType}) first and lets
     * it complete, then fetches stage list, audience user list, and comment history
     * concurrently.
     *
     * <p>Room info is deliberately sequenced <em>before</em>, not concurrent with, the other
     * three. A captured real-client session shows the same ordering: after {@code user/join},
     * the app calls {@code voice_room_info}, then — roughly a second later, after several
     * unrelated calls — {@code stage/list}. When our BFF used to fire all four concurrently,
     * a room fetched immediately after creation would 500 on {@code stage/list} indefinitely
     * (observed failing continuously for 13+ seconds with retries, never recovering) — i.e.
     * not a brief propagation lag that a retry alone can wait out, but upstream appears to
     * expect {@code voice_room_info} to have already completed for this room/session before
     * {@code stage/list} et al. will serve it.
     *
     * @param cname    room channel name
     * @param busiType business type (1 = live/video, 2 = voice — see voice-list/live-list route
     *                 dispatch on the frontend) — selects liveRoomInfo vs voiceRoomInfo
     * @return combined payload for a room join
     * @throws RuntimeException wrapping the upstream failure if any call errors
     */
    public JoinBundleResponse joinBundle(String cname, int busiType) {
        VoiceRoomInfoResponse voiceInfo;
        try {
            // Room metadata (voice or live — dispatched by busiType) including the RTC token,
            // needed by the frontend before it can open its websocket / audio connection.
            // Sequenced first — see class-level ordering note above.
            voiceInfo = withUpstreamRetry(() -> JilaliResponses.unwrap(
                    busiType == 1 ? client.liveRoomInfo(cname) : client.voiceRoomInfo(cname)));
        } catch (Exception e) {
            throw new RuntimeException("Upstream fetch failed during room join", e);
        }

        try (var scope = StructuredTaskScope.open()) {

            // Who's currently on stage.
            var stageUsersTask = scope.fork(() -> withUpstreamRetry(() ->
                    JilaliResponses.unwrap(client.stageList(busiType, cname))));
            // The audience roster. get_type=[3] matches RoomApi.fetchAudienceUsers() on the
            // frontend — omitting it (null) would ask upstream for a different, undocumented
            // default roster shape.
            var audienceUsersTask = scope.fork(() -> withUpstreamRetry(() -> JilaliResponses.unwrap(
                    client.roomUserList(new RoomUserListRequest(List.of(3), cname, busiType)))));
            // Initial chat/comment history, so the room renders with its comment feed already
            // populated instead of the frontend needing a fifth call after the bundle resolves.
            var commentsTask = scope.fork(() -> withUpstreamRetry(() ->
                    JilaliResponses.unwrap(client.comments(busiType, cname))));

            // Throws StructuredTaskScope.FailedException if any subtask fails (others are cancelled).
            // Returns normally only when all three have completed successfully.
            scope.join();

            var upstreamComments = commentsTask.get();
            return new JoinBundleResponse(
                    decryptRtcToken(voiceInfo),
                    stageUsersTask.get(),
                    audienceUsersTask.get(),
                    new CommentListDto(
                            upstreamComments.items().stream().map(this::toCommentDto).toList(),
                            upstreamComments.hasNext(),
                            upstreamComments.oldestId()
                    )
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during concurrent room join fetch", e);
        } catch (StructuredTaskScope.FailedException e) {
            throw new RuntimeException("Upstream fetch failed during room join", e.getCause());
        }
    }

    /**
     * Retries {@code call} up to {@link #MAX_UPSTREAM_ATTEMPTS} times when upstream returns a
     * 5xx (see the field doc above for why: a just-created room's own read endpoints can lag
     * briefly behind {@code create_voice_channel}). A 4xx is never retried — that is a real
     * error (bad request, room genuinely not found) that more attempts cannot fix.
     */
    private <T> T withUpstreamRetry(Callable<T> call) throws Exception {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.call();
            } catch (HttpClientResponseException e) {
                boolean serverError = e.getStatus().getCode() >= 500;
                if (!serverError || attempt >= MAX_UPSTREAM_ATTEMPTS) {
                    throw e;
                }
                log.warn("Upstream call failed (status={}), retrying attempt {}/{}",
                        e.getStatus(), attempt, MAX_UPSTREAM_ATTEMPTS - 1);
                Thread.sleep(UPSTREAM_RETRY_DELAY.toMillis());
            }
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

    /** Converts an upstream Comment (createdAt/updatedAt in Unix seconds) to CommentDto (milliseconds). */
    private CommentDto toCommentDto(Comment c) {
        return new CommentDto(
                c.id(), c.createdAt() * 1000L, c.updatedAt() * 1000L,
                c.cname(), c.busiType(), c.userId(), c.nickname(), c.headUrl(),
                c.nationality(), c.role(), c.vipType(), toMsgDto(c.msg()), c.dayRankLevel(),
                c.giftLevel(), c.fgLevel(), c.fgName(), c.fgIsActive(), c.bubbleId(),
                c.bubbleUrl(), c.bubbleColor(), c.hitBad(), c.bubbleAnimalType(),
                c.bubbleAnimalUrl(), c.vipLogo(), c.vipLogoAnim(), c.expireAt(), c.medalWallIcon()
        );
    }

    private CommentDto.Msg toMsgDto(Comment.Msg msg) {
        if (msg == null) return null;
        return new CommentDto.Msg(
                msg.text() == null ? null : new CommentDto.Msg.Text(msg.text().text()),
                toReplyInfoDto(msg.replyInfo()));
    }

    private CommentDto.Msg.ReplyInfo toReplyInfoDto(Comment.Msg.ReplyInfo r) {
        if (r == null) return null;
        return new CommentDto.Msg.ReplyInfo(r.msgId(), r.fromId(), r.fromNickname(), r.text(), r.msgType());
    }
}
