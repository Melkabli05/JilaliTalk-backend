package com.jilali.user;

import com.jilali.client.JilaliGateway;
import com.jilali.user.dto.HostStatus;
import com.jilali.user.dto.UserStatus;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;

/**
 * User room actions (join/quit) and status/profile reads. The profile endpoint is the one place
 * upstream returns binary (bin/cc2018) rather than JSON; we forward the bytes untouched with the
 * correct content type instead of pretending to parse them.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/users")
public class UserController {

    private final JilaliGateway liveHub;

    public UserController(JilaliGateway liveHub) {
        this.liveHub = liveHub;
    }

    @Post("/rooms/{cname}/join")
    public HttpResponse<Void> join(String cname, @QueryValue(defaultValue = "2") int busiType) {
        liveHub.joinRoom(cname, busiType);
        return HttpResponse.noContent();
    }

    @Post("/rooms/{cname}/quit")
    public HttpResponse<Void> quit(String cname, @QueryValue(defaultValue = "2") int busiType) {
        liveHub.quitRoom(cname, busiType);
        return HttpResponse.noContent();
    }

    @Post("/heartbeat")
    public HttpResponse<Void> heartbeat(@jakarta.validation.Valid @io.micronaut.http.annotation.Body com.jilali.user.dto.HeartbeatRequest request) {
        liveHub.heartbeat(request);
        return HttpResponse.noContent();
    }

    @Post("/rooms/list")
    public com.jilali.user.dto.RoomUserListResponse roomUsers(
            @jakarta.validation.Valid @io.micronaut.http.annotation.Body com.jilali.user.dto.RoomUserListRequest request) {
        return liveHub.roomUserList(request);
    }

    @Post("/status/batch")
    public com.jilali.user.dto.BatchStatusResponse batchStatus(
            @jakarta.validation.Valid @io.micronaut.http.annotation.Body com.jilali.user.dto.BatchStatusRequest request) {
        return liveHub.batchUserStatus(request);
    }

    @Get("/end-page/host")
    public java.util.Map<String, Object> endPageHost(
            @QueryValue(defaultValue = "2") int busiType,
            @QueryValue String cname,
            @QueryValue(value = "contributeListType", defaultValue = "topn") String contributeListType) {
        return liveHub.userEndPageHost(busiType, cname, contributeListType);
    }

    @Get("/end-page/audience")
    public java.util.Map<String, Object> endPageAudience(
            @QueryValue(defaultValue = "2") int busiType,
            @QueryValue String cname) {
        return liveHub.userEndPageAudience(busiType, cname);
    }

    @Get("/record/live")
    public java.util.Map<String, Object> recordLive(
            @QueryValue(defaultValue = "8") int limit,
            @QueryValue(defaultValue = "0") int offset) {
        return liveHub.userRecordLive(limit, offset);
    }

    @Get("/{userId}/status")
    public UserStatus status(long userId) {
        return liveHub.userStatus(userId);
    }

    @Get("/host-status")
    public HostStatus hostStatus() {
        return liveHub.hostStatus();
    }

    @Get(value = "/{userId}/profile", produces = MediaType.APPLICATION_OCTET_STREAM)
    public HttpResponse<byte[]> profile(long userId,
                                        @QueryValue String cname,
                                        @QueryValue(defaultValue = "2") int busiType) {
        var bytes = liveHub.userProfile(busiType, cname, userId);
        return HttpResponse.ok(bytes).contentType("bin/cc2018");
    }
}
