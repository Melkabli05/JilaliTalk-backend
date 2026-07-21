package com.jilali.roomcontext.application.command.room;

import com.jilali.roomcontext.domain.valueobject.BusiType;
import com.jilali.roomcontext.domain.valueobject.Cname;
import com.jilali.roomcontext.domain.valueobject.HostId;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

public final class RoomCommands {
    private RoomCommands() {}

    public record CreateVoiceRoomCommand(HostId hostId, String title, int categoryId) {}
    public record UpdateVoiceRoomCommand(Cname cname, HostId hostId, String title) {}
    public record EndRoomCommand(Cname cname, HostId hostId) {}
    public record JoinRoomCommand(Cname cname, RoomUserId userId, BusiType busiType) {}
    public record QuitRoomCommand(Cname cname, RoomUserId userId) {}
}
