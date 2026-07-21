package com.jilali.roomcontext.application.command.signin;

import com.jilali.roomcontext.domain.valueobject.Cname;
import com.jilali.roomcontext.domain.valueobject.HostId;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

public final class SignInCommands {
    private SignInCommands() {}

    public record ClaimRoomLevelRewardCommand(RoomUserId userId, Cname cname, HostId hostId, String rewardId) {}
    public record ClaimTaskRewardCommand(RoomUserId userId, Cname cname, HostId hostId, String taskId) {}
}
