package com.jilali.roomcontext.application.command.manager;

import com.jilali.roomcontext.domain.valueobject.Cname;
import com.jilali.roomcontext.domain.valueobject.HostId;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

public final class ManagerCommands {
    private ManagerCommands() {}

    public record SetManagerCommand(Cname cname, RoomUserId target, HostId grantedBy, boolean grant) {}
    public record ApproveManagerOperationCommand(Cname cname, RoomUserId target, int operationType) {}
}
