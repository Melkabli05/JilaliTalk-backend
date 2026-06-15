package com.jilali.signin;

import com.jilali.client.JilaliClient;
import com.jilali.client.JilaliResponses;
import com.jilali.room.dto.RoomLevelConfigResponse;
import com.jilali.signin.dto.ClaimRewardRequest;
import com.jilali.signin.dto.ClaimTaskRewardRequest;
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

import java.util.List;
import java.util.Map;

@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/signin")
public class SigninController {

    private final JilaliClient client;

    public SigninController(JilaliClient client) {
        this.client = client;
    }

    @Get("/panel")
    public VoiceSignPanelResponse panel(@QueryValue String cname) {
        return JilaliResponses.unwrap(client.voiceSignPanel(cname));
    }

    @Get("/tasks")
    @SuppressWarnings("unchecked")
    public VoiceTasksResponse tasks() {
        var resp = (Map<String, Object>) JilaliResponses.unwrap(client.voiceTasks());
        return new VoiceTasksResponse((List<Map<String, Object>>) resp.get("items"));
    }

    @Get("/room-level-reward")
    public RoomLevelRewardResponse roomLevelReward(
            @QueryValue String cname,
            @QueryValue("host_id") long hostId,
            @QueryValue(defaultValue = "1") int level) {
        return JilaliResponses.unwrap(client.roomLevelReward(cname, hostId, level));
    }

    @Post("/room-level-reward")
    public HttpResponse<Void> claimRoomLevelReward(@Valid @Body ClaimRewardRequest request) {
        JilaliResponses.unwrap(client.claimRoomLevelReward(request));
        return HttpResponse.noContent();
    }

    @Post("/task-reward")
    public HttpResponse<Void> claimTaskReward(@Valid @Body ClaimTaskRewardRequest request) {
        JilaliResponses.unwrap(client.voiceTaskReward(request));
        return HttpResponse.noContent();
    }

    @Get("/room-level-config")
    public RoomLevelConfigResponse roomLevelConfig(
            @QueryValue String cname,
            @QueryValue("host_id") long hostId) {
        return JilaliResponses.requireData(client.roomLevelConfig(cname, hostId));
    }
}
