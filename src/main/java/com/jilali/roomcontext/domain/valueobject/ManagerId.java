package com.jilali.roomcontext.domain.valueobject;

/**
 * The user id of a room manager (moderator) acting on the room. Distinct type from
 * {@link RoomUserId} so a manager action's actor cannot be confused with its target at a
 * call site — see {@link RoomUserId}'s Javadoc for the full rationale.
 */
public record ManagerId(long value) {

    public ManagerId {
        if (value <= 0) {
            throw new IllegalArgumentException("managerId must be positive, got " + value);
        }
    }

    public RoomUserId asRoomUserId() {
        return new RoomUserId(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
