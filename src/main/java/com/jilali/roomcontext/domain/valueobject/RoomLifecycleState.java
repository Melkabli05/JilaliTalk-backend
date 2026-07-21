package com.jilali.roomcontext.domain.valueobject;

/**
 * Explicit room lifecycle state — the legacy code has no equivalent concept at all; it infers
 * "ended" only from a room's absence in a subsequent list-rooms response. Making the state
 * explicit lets {@code Room.end()} guard against double-ending instead of trusting upstream's
 * own (unverified) tolerance for a duplicate end-room call.
 */
public enum RoomLifecycleState {
    CREATED,
    LIVE,
    ENDED
}
