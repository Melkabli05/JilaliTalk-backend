package com.jilali.roomcontext.domain.model;

import com.jilali.roomcontext.domain.exception.DomainRuleViolation;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Stage {

    private final int capacity;
    private final Map<Integer, RoomUserId> seats = new LinkedHashMap<>();
    private final Set<RoomUserId> raiseHandQueue = new LinkedHashSet<>();
    private final Set<RoomUserId> pendingInvites = new LinkedHashSet<>();

    public Stage(int capacity) {
        this.capacity = capacity;
    }

    public int assignSeat(RoomUserId userId) {
        if (seats.containsValue(userId)) {
            throw new DomainRuleViolation("User " + userId + " is already on stage");
        }
        int seat = nextFreeSeat();
        seats.put(seat, userId);
        raiseHandQueue.remove(userId);
        pendingInvites.remove(userId);
        return seat;
    }

    public void vacateSeat(RoomUserId userId) {
        seats.values().removeIf(v -> v.equals(userId));
    }

    public void queueRaiseHand(RoomUserId userId) {
        if (seats.containsValue(userId)) {
            throw new DomainRuleViolation("User " + userId + " is already on stage");
        }
        raiseHandQueue.add(userId);
    }

    public int approveRaiseHand(RoomUserId userId) {
        if (!raiseHandQueue.contains(userId)) {
            throw new DomainRuleViolation("User " + userId + " has no pending raise-hand request");
        }
        return assignSeat(userId);
    }

    public void invite(RoomUserId userId) {
        pendingInvites.add(userId);
    }

    public int approveInvite(RoomUserId userId) {
        if (!pendingInvites.contains(userId)) {
            throw new DomainRuleViolation("User " + userId + " has no pending stage invite");
        }
        return assignSeat(userId);
    }

    public void kick(RoomUserId userId) {
        vacateSeat(userId);
        raiseHandQueue.remove(userId);
        pendingInvites.remove(userId);
    }

    public Optional<Integer> seatOf(RoomUserId userId) {
        return seats.entrySet().stream()
            .filter(e -> e.getValue().equals(userId))
            .map(Map.Entry::getKey)
            .findFirst();
    }

    public Map<Integer, RoomUserId> occupants() {
        return Map.copyOf(seats);
    }

    private int nextFreeSeat() {
        for (int i = 0; i < capacity; i++) {
            if (!seats.containsKey(i)) {
                return i;
            }
        }
        throw new DomainRuleViolation("Stage is full (capacity " + capacity + ")");
    }
}
