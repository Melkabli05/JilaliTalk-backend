package com.jilali.signin;

import com.jilali.client.JilaliGateway;
import com.jilali.signin.dto.ClaimRewardRequest;
import com.jilali.signin.dto.ClaimTaskRewardRequest;
import com.jilali.signin.dto.RoomLevelRewardResponse;
import com.jilali.signin.dto.VoiceSignPanelResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.validation.Valid;

@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/signin")
public class SigninController {

    private final JilaliGateway liveHub;

    public SigninController(JilaliGateway liveHub) {
        this.liveHub = liveHub;
    }

    @Get("/panel")
    public VoiceSignPanelResponse panel(@QueryValue String cname) {
        return liveHub.voiceSignPanel(cname);
    }

    @Get("/tasks")
    public Object tasks() {
        return liveHub.voiceTasks();
    }

    @Get("/room-level-reward")
    public RoomLevelRewardResponse roomLevelReward(
            @QueryValue String cname,
            @QueryValue("host_id") long hostId,
            @QueryValue(defaultValue = "1") int level) {
        return liveHub.roomLevelReward(cname, hostId, level);
    }

    @Post("/room-level-reward")
    public HttpResponse<Void> claimRoomLevelReward(@Valid @Body ClaimRewardRequest request) {
        liveHub.claimRoomLevelReward(request);
        return HttpResponse.noContent();
    }

    @Post("/task-reward")
    public HttpResponse<Void> claimTaskReward(@Valid @Body ClaimTaskRewardRequest request) {
        liveHub.voiceTaskReward(request);
        return HttpResponse.noContent();
    }
}
