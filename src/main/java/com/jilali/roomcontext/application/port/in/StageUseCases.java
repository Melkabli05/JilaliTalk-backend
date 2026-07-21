package com.jilali.roomcontext.application.port.in;

import com.jilali.roomcontext.application.command.stage.StageCommands.ApproveRaiseHandCommand;
import com.jilali.roomcontext.application.command.stage.StageCommands.ApproveStageInviteCommand;
import com.jilali.roomcontext.application.command.stage.StageCommands.ControlStageDeviceCommand;
import com.jilali.roomcontext.application.command.stage.StageCommands.InviteToStageCommand;
import com.jilali.roomcontext.application.command.stage.StageCommands.JoinStageCommand;
import com.jilali.roomcontext.application.command.stage.StageCommands.KickFromStageCommand;
import com.jilali.roomcontext.application.command.stage.StageCommands.QuitStageCommand;
import com.jilali.roomcontext.application.command.stage.StageCommands.RaiseHandCommand;
import com.jilali.roomcontext.domain.model.Stage;
import com.jilali.roomcontext.domain.valueobject.Cname;

public interface StageUseCases {
    Stage listOccupants(Cname cname);
    void joinStage(JoinStageCommand command);
    void quitStage(QuitStageCommand command);
    void raiseHand(RaiseHandCommand command);
    void approveRaiseHand(ApproveRaiseHandCommand command);
    void kick(KickFromStageCommand command);
    void invite(InviteToStageCommand command);
    void approveInvite(ApproveStageInviteCommand command);
    void controlDevice(ControlStageDeviceCommand command);
}
