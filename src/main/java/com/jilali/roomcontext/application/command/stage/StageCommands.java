package com.jilali.roomcontext.application.command.stage;

import com.jilali.roomcontext.domain.valueobject.Cname;
import com.jilali.roomcontext.domain.valueobject.ManagerId;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

public final class StageCommands {
    private StageCommands() {}

    public record JoinStageCommand(Cname cname, RoomUserId userId) {}
    public record QuitStageCommand(Cname cname, RoomUserId userId) {}
    public record RaiseHandCommand(Cname cname, RoomUserId userId) {}
    public record ApproveRaiseHandCommand(Cname cname, RoomUserId userId, ManagerId approver) {}
    public record KickFromStageCommand(Cname cname, RoomUserId target, ManagerId actor) {}
    public record InviteToStageCommand(Cname cname, RoomUserId userId, ManagerId invitedBy) {}
    public record ApproveStageInviteCommand(Cname cname, RoomUserId userId) {}
    public record ControlStageDeviceCommand(Cname cname, RoomUserId target, ManagerId actor, int switchType, int deviceType) {}
}
