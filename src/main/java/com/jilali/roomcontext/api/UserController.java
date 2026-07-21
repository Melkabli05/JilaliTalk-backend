package com.jilali.roomcontext.api;

import com.jilali.roomcontext.application.port.out.UserUpstreamPort;
import com.jilali.roomcontext.infrastructure.dto.user.BatchStatusRequest;
import com.jilali.roomcontext.infrastructure.dto.user.BatchStatusResponse;
import com.jilali.roomcontext.infrastructure.dto.user.EnrichBatchRequest;
import com.jilali.roomcontext.infrastructure.dto.user.EnrichBatchResponse;
import com.jilali.roomcontext.infrastructure.dto.user.HeartbeatRequest;
import com.jilali.roomcontext.infrastructure.dto.user.HostStatus;
import com.jilali.roomcontext.infrastructure.dto.user.RoomUserListRequest;
import com.jilali.roomcontext.infrastructure.dto.user.RoomUserListResponse;
import com.jilali.roomcontext.infrastructure.dto.user.RoomUserProfileResponse;
import com.jilali.roomcontext.infrastructure.dto.user.UserInfo;
import com.jilali.roomcontext.infrastructure.dto.user.UserStatus;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/** New-architecture controller, temporarily mounted under {@code /api/v2} - see
 *  TranslateController's Javadoc for the coexistence rationale.
 *
 * <p>The room-scoped profile endpoint ({@code /{userId}/profile}) is decoded server-side via
 * {@code Cc2018Cipher} (see {@link com.jilali.roomcontext.infrastructure.client.UserProfileEncryptedClient#fetchRoomUserProfile})
 * rather than forwarded as raw bin/cc2018 bytes - it's the only upstream call that exposes the
 * viewer's follow relation to an arbitrary user. */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/v2/users")
public class UserController {

    private final UserUpstreamPort upstream;

    public UserController(UserUpstreamPort upstream) {
        this.upstream = upstream;
    }

    @Post("/rooms/{cname}/join")
    public HttpResponse<Void> join(@NotBlank String cname, @QueryValue(defaultValue = "2") int busiType) {
        upstream.joinRoom(cname, busiType);
        return HttpResponse.noContent();
    }

    @Post("/rooms/{cname}/quit")
    public HttpResponse<Void> quit(@NotBlank String cname, @QueryValue(defaultValue = "2") int busiType) {
        upstream.quitRoom(cname, busiType);
        return HttpResponse.noContent();
    }

    @Post("/heartbeat")
    public HttpResponse<Void> heartbeat(@Valid @Body HeartbeatRequest request) {
        upstream.heartbeat(request);
        return HttpResponse.noContent();
    }

    @Post("/rooms/list")
    public RoomUserListResponse roomUsers(@Valid @Body RoomUserListRequest request) {
        return upstream.roomUsers(request);
    }

    @Post("/status/batch")
    public BatchStatusResponse batchStatus(@Valid @Body BatchStatusRequest request) {
        return upstream.batchStatus(request);
    }

    @Post("/enrich-batch")
    public EnrichBatchResponse enrichBatch(@Valid @Body EnrichBatchRequest request) {
        return upstream.enrichBatch(request);
    }

    @Get("/end-page/host")
    public Map<String, Object> endPageHost(
            @QueryValue(defaultValue = "2") int busiType, @QueryValue String cname,
            @QueryValue(value = "contributeListType", defaultValue = "topn") String contributeListType) {
        return upstream.endPageHost(busiType, cname, contributeListType);
    }

    @Get("/end-page/audience")
    public Map<String, Object> endPageAudience(@QueryValue(defaultValue = "2") int busiType, @QueryValue String cname) {
        return upstream.endPageAudience(busiType, cname);
    }

    @Get("/record/live")
    public Map<String, Object> recordLive(@QueryValue(defaultValue = "8") int limit, @QueryValue(defaultValue = "0") int offset) {
        return upstream.recordLive(limit, offset);
    }

    @Get("/{userId}/status")
    public UserStatus status(long userId) {
        return upstream.status(userId);
    }

    @Get("/host-status")
    public HostStatus hostStatus() {
        return upstream.hostStatus();
    }

    @Get("/{userId}/profile")
    public RoomUserProfileResponse profile(long userId, @QueryValue String cname, @QueryValue(defaultValue = "2") int busiType) {
        return upstream.profile(userId, cname, busiType);
    }

    @Get("/info")
    public UserInfo userInfo(@QueryValue long userId) {
        return upstream.userInfo(userId);
    }
}
