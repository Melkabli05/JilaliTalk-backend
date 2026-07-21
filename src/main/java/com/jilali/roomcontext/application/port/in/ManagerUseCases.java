package com.jilali.roomcontext.application.port.in;

import com.jilali.roomcontext.application.command.manager.ManagerCommands.SetManagerCommand;
import com.jilali.roomcontext.domain.model.ManagerRoster;
import com.jilali.roomcontext.domain.valueobject.Cname;

public interface ManagerUseCases {
    ManagerRoster listManagers(Cname cname);
    void setManager(SetManagerCommand command);
}
