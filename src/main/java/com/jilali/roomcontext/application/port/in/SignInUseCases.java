package com.jilali.roomcontext.application.port.in;

import com.jilali.roomcontext.application.command.signin.SignInCommands.ClaimRoomLevelRewardCommand;
import com.jilali.roomcontext.application.command.signin.SignInCommands.ClaimTaskRewardCommand;
import com.jilali.roomcontext.domain.model.RoomSignIn;
import com.jilali.roomcontext.domain.model.RoomSignIn.ClaimedReward;
import com.jilali.roomcontext.domain.valueobject.Cname;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

public interface SignInUseCases {
    RoomSignIn getPanel(RoomUserId userId, Cname cname);
    ClaimedReward claimRoomLevelReward(ClaimRoomLevelRewardCommand command);
    ClaimedReward claimTaskReward(ClaimTaskRewardCommand command);
}
