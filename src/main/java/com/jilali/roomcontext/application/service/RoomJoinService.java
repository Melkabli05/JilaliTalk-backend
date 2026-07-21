package com.jilali.roomcontext.application.service;

import com.jilali.core.JilaliProperties;
import com.jilali.roomcontext.infrastructure.client.CommentJilaliClient;
import com.jilali.roomcontext.infrastructure.client.JilaliResponses;
import com.jilali.roomcontext.infrastructure.client.RoomJilaliClient;
import com.jilali.roomcontext.infrastructure.client.StageJilaliClient;
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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;

/** Native reimplementation of the legacy room.RoomJoinService's bundled-join fan-out - same
 *  StructuredTaskScope pattern (room info sequenced first, then stage/audience/comments fanned
 *  out concurrently) and the same 5xx-retry behavior for freshly-created rooms, with zero
 *  dependency on client.JilaliClient/client.JilaliResponses. */
@Singleton
public class RoomJoinService {

    private static final Logger log = LoggerFactory.getLogger(RoomJoinService.class);
    private static final int MAX_UPSTREAM_ATTEMPTS = 4;
    private static final Duration UPSTREAM_RETRY_DELAY = Duration.ofMillis(700);

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
        VoiceRoomInfoResponse voiceInfo;
        try {
            voiceInfo = withUpstreamRetry(() -> JilaliResponses.unwrap(
                    busiType == 1 ? roomClient.liveRoomInfo(cname) : roomClient.voiceRoomInfo(cname)));
        } catch (HttpClientResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Upstream fetch failed during room join", e);
        }

        try (var scope = StructuredTaskScope.open()) {
            var stageUsersTask = scope.fork(() -> withUpstreamRetry(() ->
                    JilaliResponses.unwrap(stageClient.stageList(busiType, cname))));
            var audienceUsersTask = scope.fork(() -> withUpstreamRetry(() -> JilaliResponses.unwrap(
                    userClient.roomUserList(new RoomUserListRequest(List.of(3), cname, busiType)))));
            var commentsTask = scope.fork(() -> withUpstreamRetry(() ->
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

    <T> T withUpstreamRetry(Callable<T> call) throws Exception {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.call();
            } catch (HttpClientResponseException e) {
                boolean serverError = e.getStatus().getCode() >= 500;
                String upstreamBody = e.getResponse() != null
                        ? e.getResponse().getBody(String.class).orElse("<empty>")
                        : "<no response>";
                if (!serverError || attempt >= MAX_UPSTREAM_ATTEMPTS) {
                    log.warn("Upstream call failed permanently (status={}): {}", e.getStatus(), upstreamBody);
                    throw e;
                }
                log.warn("Upstream call failed (status={}), retrying attempt {}/{}: {}",
                        e.getStatus(), attempt, MAX_UPSTREAM_ATTEMPTS - 1, upstreamBody);
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
}
