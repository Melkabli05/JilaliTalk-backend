package com.jilali.roomcontext.domain.valueobject;

/**
 * Replaces the legacy {@code room.dto.RoomUser}'s 4 independent booleans ({@code isOnMic},
 * {@code isTurnOnMic}, {@code isTurnOnCam}, {@code isRaiseHand}) with one closed, always-valid
 * state. The legacy shape legally allows nonsensical combinations — e.g. {@code isOnMic=false,
 * isTurnOnMic=true} has no defined meaning — because nothing enforces that the 4 booleans move
 * together. A sealed interface makes the illegal states unrepresentable instead of merely
 * undocumented.
 *
 * <p>{@code camOn} is carried as a field on {@link Speaking} rather than as its own state,
 * since camera-on-or-off is only a meaningful distinction once a member is actually speaking —
 * a member who isn't on stage has no camera state to track (this mirrors the legacy
 * {@code isTurnOnCam} field, which the wire format only populates meaningfully alongside
 * {@code isOnMic=true}).
 */
public sealed interface MicState {

    /** Not on stage at all — the default state for a room member. */
    record Off() implements MicState {}

    /** Waiting in the raise-hand queue, not yet approved onto stage. */
    record PendingApproval() implements MicState {}

    /** On a stage seat, not currently transmitting audio (muted). */
    record Listening() implements MicState {}

    /** On a stage seat, actively transmitting audio, with a known camera state. */
    record Speaking(boolean camOn) implements MicState {}
}
