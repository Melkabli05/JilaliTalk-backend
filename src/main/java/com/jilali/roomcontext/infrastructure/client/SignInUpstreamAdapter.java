package com.jilali.roomcontext.infrastructure.client;

import com.jilali.roomcontext.application.port.out.SignInUpstreamPort;
import com.jilali.roomcontext.infrastructure.dto.room.RoomLevelConfigResponse;
import com.jilali.roomcontext.infrastructure.dto.signin.ClaimRewardRequest;
import com.jilali.roomcontext.infrastructure.dto.signin.ClaimTaskRewardRequest;
import com.jilali.roomcontext.infrastructure.dto.signin.RoomLevelRewardResponse;
import com.jilali.roomcontext.infrastructure.dto.signin.VoiceSignPanelResponse;
import com.jilali.roomcontext.infrastructure.dto.signin.VoiceTasksResponse;
import jakarta.inject.Singleton;

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
    public VoiceTasksResponse tasks() {
        return JilaliResponses.unwrap(client.voiceTasks());
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
