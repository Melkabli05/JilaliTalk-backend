package com.jilali.roomcontext.application.port.out;

import com.jilali.room.dto.RoomLevelConfigResponse;
import com.jilali.signin.dto.ClaimRewardRequest;
import com.jilali.signin.dto.ClaimTaskRewardRequest;
import com.jilali.signin.dto.RoomLevelRewardResponse;
import com.jilali.signin.dto.VoiceSignPanelResponse;
import com.jilali.signin.dto.VoiceTasksResponse;

public interface SignInUpstreamPort {
    VoiceSignPanelResponse panel(String cname);
    VoiceTasksResponse tasks();
    RoomLevelRewardResponse roomLevelReward(String cname, long hostId, int level);
    void claimRoomLevelReward(ClaimRewardRequest request);
    void claimTaskReward(ClaimTaskRewardRequest request);
    RoomLevelConfigResponse roomLevelConfig(String cname, long hostId);
}
