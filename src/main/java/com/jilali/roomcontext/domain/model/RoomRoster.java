package com.jilali.roomcontext.domain.model;

import com.jilali.roomcontext.domain.exception.DomainRuleViolation;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RoomRoster {

    private final Map<RoomUserId, RoomMember> members = new LinkedHashMap<>();

    public RoomMember join(RoomUserId userId) {
        return members.computeIfAbsent(userId, RoomMember::new);
    }

    public void leave(RoomUserId userId) {
        members.remove(userId);
    }

    public RoomMember require(RoomUserId userId) {
        RoomMember member = members.get(userId);
        if (member == null) {
            throw new DomainRuleViolation("User " + userId + " is not a member of this room");
        }
        return member;
    }

    public boolean contains(RoomUserId userId) {
        return members.containsKey(userId);
    }

    public Collection<RoomMember> members() {
        return members.values();
    }

    public int size() {
        return members.size();
    }
}
