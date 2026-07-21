package com.jilali.roomcontext.application.port.in;

import com.jilali.roomcontext.application.command.room.RoomCommands.CreateVoiceRoomCommand;
import com.jilali.roomcontext.application.command.room.RoomCommands.EndRoomCommand;
import com.jilali.roomcontext.application.command.room.RoomCommands.JoinRoomCommand;
import com.jilali.roomcontext.application.command.room.RoomCommands.QuitRoomCommand;
import com.jilali.roomcontext.domain.model.Room;
import com.jilali.roomcontext.domain.valueobject.Cname;

public interface RoomUseCases {
    Room createVoiceRoom(CreateVoiceRoomCommand command);
    Room getRoom(Cname cname);
    void joinRoom(JoinRoomCommand command);
    void quitRoom(QuitRoomCommand command);
    void endRoom(EndRoomCommand command);
}
