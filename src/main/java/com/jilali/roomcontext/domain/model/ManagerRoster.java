package com.jilali.roomcontext.domain.model;

import com.jilali.roomcontext.domain.valueobject.RoomUserId;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ManagerRoster {

    private final Set<RoomUserId> managers = new LinkedHashSet<>();

    public void grant(RoomUserId userId) {
        managers.add(userId);
    }

    public void revoke(RoomUserId userId) {
        managers.remove(userId);
    }

    public boolean isManager(RoomUserId userId) {
        return managers.contains(userId);
    }

    public Set<RoomUserId> all() {
        return Set.copyOf(managers);
    }
}
