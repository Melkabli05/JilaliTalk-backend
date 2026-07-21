package com.jilali.roomcontext.infrastructure.client;

import com.jilali.roomcontext.application.port.out.SignInUpstreamPort;
import com.jilali.roomcontext.infrastructure.dto.room.RoomLevelConfigResponse;
import com.jilali.roomcontext.infrastructure.dto.signin.ClaimRewardRequest;
import com.jilali.roomcontext.infrastructure.dto.signin.ClaimTaskRewardRequest;
import com.jilali.roomcontext.infrastructure.dto.signin.RoomLevelRewardResponse;
import com.jilali.roomcontext.infrastructure.dto.signin.VoiceSignPanelResponse;
import com.jilali.roomcontext.infrastructure.dto.signin.VoiceTasksResponse;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

@Singleton
public class SignInUpstreamAdapter implements SignInUpstreamPort {

    private final SignInJilaliClient client;

    public SignInUpstreamAdapter(SignInJilaliClient client) {
        this.client = client;
    }

    @Override
    public VoiceSignPanelResponse panel(String cname) {
        return JilaliResponses.unwrap(client.voiceSignPanel(cname));
    }

    @Override
    @SuppressWarnings("unchecked")
    public VoiceTasksResponse tasks() {
        var resp = (Map<String, Object>) JilaliResponses.unwrap(client.voiceTasks());
        return new VoiceTasksResponse((List<Map<String, Object>>) resp.get("items"));
    }

    @Override
    public RoomLevelRewardResponse roomLevelReward(String cname, long hostId, int level) {
        return JilaliResponses.unwrap(client.roomLevelReward(cname, hostId, level));
    }

    @Override
    public void claimRoomLevelReward(ClaimRewardRequest request) {
        JilaliResponses.unwrap(client.claimRoomLevelReward(request));
    }

    @Override
    public void claimTaskReward(ClaimTaskRewardRequest request) {
        JilaliResponses.unwrap(client.voiceTaskReward(request));
    }

    @Override
    public RoomLevelConfigResponse roomLevelConfig(String cname, long hostId) {
        return JilaliResponses.requireData(client.roomLevelConfig(cname, hostId));
    }
}
