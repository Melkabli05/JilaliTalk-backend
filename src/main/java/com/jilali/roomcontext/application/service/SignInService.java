package com.jilali.roomcontext.application.service;

import com.jilali.roomcontext.application.command.signin.SignInCommands.ClaimRoomLevelRewardCommand;
import com.jilali.roomcontext.application.command.signin.SignInCommands.ClaimTaskRewardCommand;
import com.jilali.roomcontext.application.port.in.SignInUseCases;
import com.jilali.roomcontext.application.port.out.SignInRepositoryPort;
import com.jilali.roomcontext.domain.exception.DomainRuleViolation;
import com.jilali.roomcontext.domain.model.RoomSignIn;
import com.jilali.roomcontext.domain.model.RoomSignIn.ClaimedReward;
import com.jilali.roomcontext.domain.valueobject.Cname;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

import jakarta.inject.Singleton;

@Singleton
public class SignInService implements SignInUseCases {

    private final SignInRepositoryPort signIns;

    public SignInService(SignInRepositoryPort signIns) {
        this.signIns = signIns;
    }

    @Override
    public RoomSignIn getPanel(RoomUserId userId, Cname cname) {
        return signIns.find(userId, cname)
            .orElseThrow(() -> new DomainRuleViolation("No sign-in state for user " + userId + " in room " + cname));
    }

    @Override
    public ClaimedReward claimRoomLevelReward(ClaimRoomLevelRewardCommand command) {
        RoomSignIn signIn = getPanel(command.userId(), command.cname());
        ClaimedReward claimed = signIn.claim(command.rewardId());
        signIns.save(signIn);
        return claimed;
    }

    @Override
    public ClaimedReward claimTaskReward(ClaimTaskRewardCommand command) {
        RoomSignIn signIn = getPanel(command.userId(), command.cname());
        ClaimedReward claimed = signIn.claimTask(command.taskId());
        signIns.save(signIn);
        return claimed;
    }
}
