package com.jilali.roomcontext.application.service;

import com.jilali.roomcontext.application.command.manager.ManagerCommands.SetManagerCommand;
import com.jilali.roomcontext.application.port.in.ManagerUseCases;
import com.jilali.roomcontext.application.port.out.RoomEventPublisherPort;
import com.jilali.roomcontext.application.port.out.RoomRepositoryPort;
import com.jilali.roomcontext.domain.event.RoomEvent;
import com.jilali.roomcontext.domain.exception.DomainRuleViolation;
import com.jilali.roomcontext.domain.model.ManagerRoster;
import com.jilali.roomcontext.domain.model.Room;
import com.jilali.roomcontext.domain.valueobject.Cname;

import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class ManagerService implements ManagerUseCases {

    private final RoomRepositoryPort rooms;
    private final RoomEventPublisherPort events;

    public ManagerService(RoomRepositoryPort rooms, RoomEventPublisherPort events) {
        this.rooms = rooms;
        this.events = events;
    }

    @Override
    public ManagerRoster listManagers(Cname cname) {
        return room(cname).managers();
    }

    @Override
    public void setManager(SetManagerCommand command) {
        Room room = room(command.cname());
        if (command.grant()) {
            room.grantManager(command.target(), command.grantedBy());
        } else {
            room.revokeManager(command.target(), command.grantedBy());
        }
        rooms.save(room);
        List<RoomEvent> pending = room.pullPendingEvents();
        pending.forEach(events::publish);
    }

    private Room room(Cname cname) {
        return rooms.findByCname(cname)
            .orElseThrow(() -> new DomainRuleViolation("Room " + cname + " not found"));
    }
}
