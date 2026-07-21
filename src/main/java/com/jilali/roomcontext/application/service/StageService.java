package com.jilali.roomcontext.application.service;

import com.jilali.roomcontext.application.command.stage.StageCommands.ApproveRaiseHandCommand;
import com.jilali.roomcontext.application.command.stage.StageCommands.ApproveStageInviteCommand;
import com.jilali.roomcontext.application.command.stage.StageCommands.ControlStageDeviceCommand;
import com.jilali.roomcontext.application.command.stage.StageCommands.InviteToStageCommand;
import com.jilali.roomcontext.application.command.stage.StageCommands.JoinStageCommand;
import com.jilali.roomcontext.application.command.stage.StageCommands.KickFromStageCommand;
import com.jilali.roomcontext.application.command.stage.StageCommands.QuitStageCommand;
import com.jilali.roomcontext.application.command.stage.StageCommands.RaiseHandCommand;
import com.jilali.roomcontext.application.port.in.StageUseCases;
import com.jilali.roomcontext.application.port.out.RoomEventPublisherPort;
import com.jilali.roomcontext.application.port.out.RoomRepositoryPort;
import com.jilali.roomcontext.domain.event.RoomEvent;
import com.jilali.roomcontext.domain.exception.DomainRuleViolation;
import com.jilali.roomcontext.domain.model.Room;
import com.jilali.roomcontext.domain.model.Stage;
import com.jilali.roomcontext.domain.valueobject.Cname;

import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class StageService implements StageUseCases {

    private final RoomRepositoryPort rooms;
    private final RoomEventPublisherPort events;

    public StageService(RoomRepositoryPort rooms, RoomEventPublisherPort events) {
        this.rooms = rooms;
        this.events = events;
    }

    @Override
    public Stage listOccupants(Cname cname) {
        return room(cname).stage();
    }

    @Override
    public void joinStage(JoinStageCommand command) {
        Room room = room(command.cname());
        room.assignStageSeat(command.userId());
        persist(room);
    }

    @Override
    public void quitStage(QuitStageCommand command) {
        Room room = room(command.cname());
        room.vacateStageSeat(command.userId());
        persist(room);
    }

    @Override
    public void raiseHand(RaiseHandCommand command) {
        Room room = room(command.cname());
        room.queueRaiseHand(command.userId());
        persist(room);
    }

    @Override
    public void approveRaiseHand(ApproveRaiseHandCommand command) {
        Room room = room(command.cname());
        room.approveRaiseHand(command.userId(), command.approver());
        persist(room);
    }

    @Override
    public void kick(KickFromStageCommand command) {
        Room room = room(command.cname());
        room.kick(command.target(), command.actor());
        persist(room);
    }

    @Override
    public void invite(InviteToStageCommand command) {
        Room room = room(command.cname());
        room.inviteToStage(command.userId(), command.invitedBy());
        persist(room);
    }

    @Override
    public void approveInvite(ApproveStageInviteCommand command) {
        Room room = room(command.cname());
        room.approveStageInvite(command.userId());
        persist(room);
    }

    @Override
    public void controlDevice(ControlStageDeviceCommand command) {
        Room room = room(command.cname());
        room.controlStageDevice(command.target(), command.actor(), command.deviceType() == 1);
        persist(room);
    }

    private Room room(Cname cname) {
        return rooms.findByCname(cname)
            .orElseThrow(() -> new DomainRuleViolation("Room " + cname + " not found"));
    }

    private void persist(Room room) {
        rooms.save(room);
        List<RoomEvent> pending = room.pullPendingEvents();
        pending.forEach(events::publish);
    }
}
