package com.jilali.roomcontext.api;

import com.jilali.room.dto.RoomLevelConfigResponse;
import com.jilali.roomcontext.application.port.out.SignInUpstreamPort;
import com.jilali.roomcontext.application.service.SignInBundleService;
import com.jilali.signin.dto.ClaimRewardRequest;
import com.jilali.signin.dto.ClaimTaskRewardRequest;
import com.jilali.signin.dto.RoomLevelBundleResponse;
import com.jilali.signin.dto.RoomLevelRewardResponse;
import com.jilali.signin.dto.VoiceSignPanelResponse;
import com.jilali.signin.dto.VoiceTasksResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.Valid;

/** New-architecture controller, temporarily mounted under {@code /api/v2} - see
 *  TranslateController's Javadoc for the coexistence rationale. */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/v2/signin")
public class SignInController {

    private final SignInUpstreamPort upstream;
    private final SignInBundleService bundleService;

    public SignInController(SignInUpstreamPort upstream, SignInBundleService bundleService) {
        this.upstream = upstream;
        this.bundleService = bundleService;
    }

    @Get("/panel")
    public VoiceSignPanelResponse panel(@QueryValue String cname) {
        return upstream.panel(cname);
    }

    @Get("/tasks")
    public VoiceTasksResponse tasks() {
        return upstream.tasks();
    }

    @Get("/room-level-reward")
    public RoomLevelRewardResponse roomLevelReward(
            @QueryValue String cname, @QueryValue("host_id") long hostId,
            @QueryValue(defaultValue = "1") int level) {
        return upstream.roomLevelReward(cname, hostId, level);
    }

    @Post("/room-level-reward")
    public HttpResponse<Void> claimRoomLevelReward(@Valid @Body ClaimRewardRequest request) {
        upstream.claimRoomLevelReward(request);
        return HttpResponse.noContent();
    }

    @Post("/task-reward")
    public HttpResponse<Void> claimTaskReward(@Valid @Body ClaimTaskRewardRequest request) {
        upstream.claimTaskReward(request);
        return HttpResponse.noContent();
    }

    @Get("/room-level-config")
    public RoomLevelConfigResponse roomLevelConfig(@QueryValue String cname, @QueryValue("host_id") long hostId) {
        return upstream.roomLevelConfig(cname, hostId);
    }

    @Get("/room-level-bundle")
    public RoomLevelBundleResponse roomLevelBundle(
            @QueryValue String cname, @QueryValue("host_id") long hostId,
            @QueryValue(defaultValue = "1") int level) {
        return bundleService.roomLevelBundle(cname, hostId, level);
    }
}
