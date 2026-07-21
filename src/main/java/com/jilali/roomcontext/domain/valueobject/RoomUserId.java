package com.jilali.roomcontext.domain.valueobject;

/**
 * A HelloTalk user id, as seen from inside a room context (a room member, a comment author, a
 * profile being looked up, ...). Kept distinct from {@link HostId} and {@link ManagerId} — both
 * are also just {@code long} user ids in the legacy code, and using the same bare type for
 * "the actor" and "the target" of an action is a real bug class (an accidentally swapped
 * argument pair compiles fine when both are {@code long}; it doesn't compile when the types
 * differ).
 */
public record RoomUserId(long value) {

    public RoomUserId {
        if (value <= 0) {
            throw new IllegalArgumentException("userId must be positive, got " + value);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
