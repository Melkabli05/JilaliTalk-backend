package com.jilali.roomcontext.application.port.out;

import com.jilali.roomcontext.infrastructure.dto.room.RoomLevelConfigResponse;
import com.jilali.roomcontext.infrastructure.dto.signin.ClaimRewardRequest;
import com.jilali.roomcontext.infrastructure.dto.signin.ClaimTaskRewardRequest;
import com.jilali.roomcontext.infrastructure.dto.signin.RoomLevelRewardResponse;
import com.jilali.roomcontext.infrastructure.dto.signin.VoiceSignPanelResponse;
import com.jilali.roomcontext.infrastructure.dto.signin.VoiceTasksResponse;

public interface SignInUpstreamPort {
    VoiceSignPanelResponse panel(String cname);
    VoiceTasksResponse tasks();
    RoomLevelRewardResponse roomLevelReward(String cname, long hostId, int level);
    void claimRoomLevelReward(ClaimRewardRequest request);
    void claimTaskReward(ClaimTaskRewardRequest request);
    RoomLevelConfigResponse roomLevelConfig(String cname, long hostId);
}
