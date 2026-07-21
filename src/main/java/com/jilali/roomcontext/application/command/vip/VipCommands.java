package com.jilali.roomcontext.application.command.vip;

import com.jilali.roomcontext.domain.valueobject.RoomUserId;

public final class VipCommands {
    private VipCommands() {}

    public record ClaimVipTrialCommand(RoomUserId userId) {}
    public record UseVipCardCommand(RoomUserId userId, String cardId, String featureId) {}
    public record ReceiveFriendCardCommand(RoomUserId sender, RoomUserId receiver, String cardId) {}
}
