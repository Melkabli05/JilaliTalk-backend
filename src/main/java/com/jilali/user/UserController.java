package com.jilali.user;

import com.jilali.client.JilaliClient;
import com.jilali.client.JilaliGateway;
import com.jilali.client.JilaliResponses;
import com.jilali.user.dto.BatchStatusRequest;
import com.jilali.user.dto.BatchStatusResponse;
import com.jilali.user.dto.EnrichBatchRequest;
import com.jilali.user.dto.EnrichBatchResponse;
import com.jilali.user.dto.HeartbeatRequest;
import com.jilali.user.dto.HostStatus;
import com.jilali.user.dto.RoomUserListRequest;
import com.jilali.user.dto.RoomUserListResponse;
import com.jilali.user.dto.UserInfo;
import com.jilali.user.dto.UserStatus;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
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

/**
 * User room actions (join/quit) and status/profile reads. The profile endpoint is the one place
 * upstream returns binary (bin/cc2018) rather than JSON; we forward the bytes untouched with the
 * correct content type instead of pretending to parse them.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/users")
public class UserController {

    private final JilaliGateway gateway;

    public UserController(JilaliGateway gateway) {
        this.gateway = gateway;
    }

    @Post("/rooms/{cname}/join")
    public HttpResponse<Void> join(@NotBlank String cname, @QueryValue(defaultValue = "2") int busiType) {
        JilaliResponses.unwrap(gateway.client().joinRoom(new JilaliClient.JoinQuitRequest(cname, busiType)));
        return HttpResponse.noContent();
    }

    @Post("/rooms/{cname}/quit")
    public HttpResponse<Void> quit(@NotBlank String cname, @QueryValue(defaultValue = "2") int busiType) {
        JilaliResponses.unwrap(gateway.client().quitRoom(new JilaliClient.JoinQuitRequest(cname, busiType)));
        return HttpResponse.noContent();
    }

    @Post("/heartbeat")
    public HttpResponse<Void> heartbeat(@Valid @Body HeartbeatRequest request) {
        JilaliResponses.unwrap(gateway.client().heartbeat(request));
        return HttpResponse.noContent();
    }

    @Post("/rooms/list")
    public RoomUserListResponse roomUsers(@Valid @Body RoomUserListRequest request) {
        return JilaliResponses.unwrap(gateway.client().roomUserList(request));
    }

    @Post("/status/batch")
    public BatchStatusResponse batchStatus(@Valid @Body BatchStatusRequest request) {
        return JilaliResponses.unwrap(gateway.client().batchUserStatus(request));
    }

    /**
     * Batch enrichment for user profiles. Resolves a list of user IDs to their full profiles
     * in a single call — warm entries (already in the user-info cache) are free; cold entries
     * pay one encrypted upstream call each.
     *
     * <p>This replaces the per-user fetch pattern in AudienceStore.enrichAudienceUser(),
     * StageStore.enrichStageUser(), and ghost-audience.util.ts where each user_join event
     * triggered a separate GET /users/info?userId=... call.
     */
    @Post("/enrich-batch")
    public EnrichBatchResponse enrichBatch(@Valid @Body EnrichBatchRequest request) {
        return new EnrichBatchResponse(
                request.userIds().stream()
                        .map(gateway::userInfo)
                        .filter(u -> u != null)
                        .toList()
        );
    }

    @Get("/end-page/host")
    public Map<String, Object> endPageHost(
            @QueryValue(defaultValue = "2") int busiType,
            @QueryValue String cname,
            @QueryValue(value = "contributeListType", defaultValue = "topn") String contributeListType) {
        return JilaliResponses.unwrap(gateway.client().userEndPageHost(busiType, cname, contributeListType));
    }

    @Get("/end-page/audience")
    public Map<String, Object> endPageAudience(
            @QueryValue(defaultValue = "2") int busiType,
            @QueryValue String cname) {
        return JilaliResponses.unwrap(gateway.client().userEndPageAudience(busiType, cname));
    }

    @Get("/record/live")
    public Map<String, Object> recordLive(
            @QueryValue(defaultValue = "8") int limit,
            @QueryValue(defaultValue = "0") int offset) {
        return JilaliResponses.unwrap(gateway.client().userRecordLive(limit, offset));
    }

    @Get("/{userId}/status")
    public UserStatus status(long userId) {
        return JilaliResponses.unwrap(gateway.client().userStatus(userId));
    }

    @Get("/host-status")
    public HostStatus hostStatus() {
        return JilaliResponses.unwrap(gateway.client().hostStatus());
    }

    @Get(value = "/{userId}/profile", produces = MediaType.APPLICATION_OCTET_STREAM)
    public HttpResponse<byte[]> profile(long userId,
                                        @QueryValue String cname,
                                        @QueryValue(defaultValue = "2") int busiType) {
        var bytes = gateway.client().userProfile(busiType, cname, userId);
        return HttpResponse.ok(bytes).contentType("bin/cc2018");
    }

    @Get("/info")
    public UserInfo userInfo(@QueryValue long userId) {
        return gateway.userInfo(userId);
    }
}
