package com.jilali.roomcontext.application.service;

import com.jilali.core.JilaliProperties;
import com.jilali.roomcontext.infrastructure.client.CommentJilaliClient;
import com.jilali.roomcontext.infrastructure.client.JilaliResponses;
import com.jilali.roomcontext.infrastructure.client.RoomJilaliClient;
import com.jilali.roomcontext.infrastructure.client.StageJilaliClient;
import com.jilali.roomcontext.infrastructure.client.UpstreamRetry;
import com.jilali.roomcontext.infrastructure.client.UserJilaliClient;
import com.jilali.roomcontext.infrastructure.crypto.AgoraTokenCipher;
import com.jilali.roomcontext.infrastructure.dto.comment.Comment;
import com.jilali.roomcontext.infrastructure.dto.comment.CommentListDto;
import com.jilali.roomcontext.infrastructure.dto.room.JoinBundleResponse;
import com.jilali.roomcontext.infrastructure.dto.room.VoiceRoomInfoResponse;
import com.jilali.roomcontext.infrastructure.dto.user.RoomUserListRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

/** Native reimplementation of the legacy room.RoomJoinService's bundled-join fan-out - same
 *  StructuredTaskScope pattern (room info sequenced first, then stage/audience/comments fanned
 *  out concurrently), with zero dependency on client.JilaliClient/client.JilaliResponses. The
 *  5xx-retry behavior for freshly-created rooms lives in the shared UpstreamRetry helper.
 *
 * <p>Room info is deliberately sequenced <em>before</em>, not concurrent with, the other three
 * calls. A captured real-client session shows the same ordering: after {@code user/join}, the
 * app calls {@code voice_room_info}, then - roughly a second later, after several unrelated
 * calls - {@code stage/list}. When this BFF used to fire all four concurrently, a room fetched
 * immediately after creation would 500 on {@code stage/list} indefinitely (observed failing
 * continuously for 13+ seconds with retries, never recovering) - i.e. not a brief propagation
 * lag that a retry alone can wait out, but upstream appears to expect {@code voice_room_info}
 * to have already completed for this room/session before {@code stage/list} et al. will serve
 * it. */
@Singleton
public class RoomJoinService {

    private static final Logger log = LoggerFactory.getLogger(RoomJoinService.class);

    private final RoomJilaliClient roomClient;
    private final StageJilaliClient stageClient;
    private final UserJilaliClient userClient;
    private final CommentJilaliClient commentClient;
    private final JilaliProperties properties;

    public RoomJoinService(RoomJilaliClient roomClient, StageJilaliClient stageClient,
                            UserJilaliClient userClient, CommentJilaliClient commentClient,
                            JilaliProperties properties) {
        this.roomClient = roomClient;
        this.stageClient = stageClient;
        this.userClient = userClient;
        this.commentClient = commentClient;
        this.properties = properties;
    }

    public JoinBundleResponse joinBundle(String cname, int busiType) {
        VoiceRoomInfoResponse voiceInfo = UpstreamRetry.withRetry(() -> JilaliResponses.unwrap(
                busiType == 1 ? roomClient.liveRoomInfo(cname) : roomClient.voiceRoomInfo(cname)));

        try (var scope = StructuredTaskScope.open()) {
            var stageUsersTask = scope.fork(() -> UpstreamRetry.withRetry(() ->
                    JilaliResponses.unwrap(stageClient.stageList(busiType, cname))));
            // get_type=[3] matches RoomApi.fetchAudienceUsers() on the frontend - omitting it
            // (null) would ask upstream for a different, undocumented default roster shape.
            var audienceUsersTask = scope.fork(() -> UpstreamRetry.withRetry(() -> JilaliResponses.unwrap(
                    userClient.roomUserList(new RoomUserListRequest(List.of(3), cname, busiType)))));
            var commentsTask = scope.fork(() -> UpstreamRetry.withRetry(() ->
                    JilaliResponses.unwrap(commentClient.comments(busiType, cname))));

            scope.join();

            var upstreamComments = commentsTask.get();
            return new JoinBundleResponse(
                    decryptRtcToken(voiceInfo),
                    stageUsersTask.get(),
                    audienceUsersTask.get(),
                    new CommentListDto(
                            upstreamComments.items().stream().map(Comment::fromWireSeconds).toList(),
                            upstreamComments.hasNext(),
                            upstreamComments.oldestId()
                    )
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during concurrent room join fetch", e);
        } catch (StructuredTaskScope.FailedException e) {
            if (e.getCause() instanceof HttpClientResponseException httpEx) {
                throw httpEx;
            }
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
