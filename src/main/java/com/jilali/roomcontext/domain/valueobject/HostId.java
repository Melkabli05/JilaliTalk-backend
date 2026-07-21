package com.jilali.roomcontext.domain.valueobject;

/**
 * The user id of a room's host. Distinct type from {@link RoomUserId} so a method like
 * {@code Room.grantManager(RoomUserId target, HostId grantedBy)} cannot have its two
 * {@code long}-shaped arguments accidentally swapped at the call site — see {@link RoomUserId}'s
 * Javadoc for the full rationale.
 */
public record HostId(long value) {

    public HostId {
        if (value <= 0) {
            throw new IllegalArgumentException("hostId must be positive, got " + value);
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
