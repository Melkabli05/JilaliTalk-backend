package com.jilali.roomcontext.domain.event;

import com.jilali.roomcontext.domain.model.Comment;
import com.jilali.roomcontext.domain.valueobject.Cname;
import com.jilali.roomcontext.domain.valueobject.HostId;
import com.jilali.roomcontext.domain.valueobject.ManagerId;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

public sealed interface RoomEvent {
    Cname cname();

    record MemberJoined(Cname cname, RoomUserId userId) implements RoomEvent {}
    record MemberLeft(Cname cname, RoomUserId userId) implements RoomEvent {}
    record StageSeatTaken(Cname cname, RoomUserId userId, int seat) implements RoomEvent {}
    record StageSeatVacated(Cname cname, RoomUserId userId) implements RoomEvent {}
    record RaiseHandQueued(Cname cname, RoomUserId userId) implements RoomEvent {}
    record RaiseHandApproved(Cname cname, RoomUserId userId, ManagerId approver) implements RoomEvent {}
    record MemberKicked(Cname cname, RoomUserId target, ManagerId actor) implements RoomEvent {}
    record ManagerGranted(Cname cname, RoomUserId target, HostId by) implements RoomEvent {}
    record ManagerRevoked(Cname cname, RoomUserId target, HostId by) implements RoomEvent {}
    record CommentPosted(Cname cname, Comment comment) implements RoomEvent {}
    record RoomEnded(Cname cname, HostId by) implements RoomEvent {}
}
