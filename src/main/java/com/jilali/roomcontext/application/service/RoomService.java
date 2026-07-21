package com.jilali.roomcontext.application.service;

import com.jilali.roomcontext.application.command.room.RoomCommands.CreateVoiceRoomCommand;
import com.jilali.roomcontext.application.command.room.RoomCommands.EndRoomCommand;
import com.jilali.roomcontext.application.command.room.RoomCommands.JoinRoomCommand;
import com.jilali.roomcontext.application.command.room.RoomCommands.QuitRoomCommand;
import com.jilali.roomcontext.application.port.in.RoomUseCases;
import com.jilali.roomcontext.application.port.out.RoomEventPublisherPort;
import com.jilali.roomcontext.application.port.out.RoomRepositoryPort;
import com.jilali.roomcontext.domain.event.RoomEvent;
import com.jilali.roomcontext.domain.exception.DomainRuleViolation;
import com.jilali.roomcontext.domain.model.Room;
import com.jilali.roomcontext.domain.valueobject.BusiType;
import com.jilali.roomcontext.domain.valueobject.Cname;
import com.jilali.roomcontext.domain.valueobject.RoomLevel;

import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;

@Singleton
public class RoomService implements RoomUseCases {

    private static final int DEFAULT_STAGE_CAPACITY = 8;

    private final RoomRepositoryPort rooms;
    private final RoomEventPublisherPort events;

    public RoomService(RoomRepositoryPort rooms, RoomEventPublisherPort events) {
        this.rooms = rooms;
        this.events = events;
    }

    @Override
    public Room createVoiceRoom(CreateVoiceRoomCommand command) {
        Cname cname = new Cname(UUID.randomUUID().toString());
        Room room = new Room(cname, command.hostId(), BusiType.VOICE, DEFAULT_STAGE_CAPACITY,
            new RoomLevel(1, 0, List.of()));
        room.goLive();
        rooms.save(room);
        publishPendingEvents(room);
        return room;
    }

    @Override
    public Room getRoom(Cname cname) {
        return rooms.findByCname(cname)
            .orElseThrow(() -> new DomainRuleViolation("Room " + cname + " not found"));
    }

    @Override
    public void joinRoom(JoinRoomCommand command) {
        Room room = getRoom(command.cname());
        room.join(command.userId());
        rooms.save(room);
        publishPendingEvents(room);
    }

    @Override
    public void quitRoom(QuitRoomCommand command) {
        Room room = getRoom(command.cname());
        room.leave(command.userId());
        rooms.save(room);
        publishPendingEvents(room);
    }

    @Override
    public void endRoom(EndRoomCommand command) {
        Room room = getRoom(command.cname());
        room.end(command.hostId());
        rooms.save(room);
        publishPendingEvents(room);
    }

    private void publishPendingEvents(Room room) {
        List<RoomEvent> pending = room.pullPendingEvents();
        pending.forEach(events::publish);
    }
}
