package com.jilali.room.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Collapses the audience-reconciliation poll's two round trips (revision check, then a
 * conditional roster refetch) into one. If the caller's {@code sinceRevision} already matches
 * the server-side revision, {@code changed} is {@code false} and {@code list} is omitted — no
 * upstream call is made. Only a real roster change costs an upstream round trip.
 *
 * @param revision  current server-side audience revision (see
 *                  {@code RoomEventSource#audienceRevision}).
 * @param changed   true when {@code revision} moved past the caller's {@code sinceRevision}.
 * @param list      the current roster, present only when {@code changed} is true.
 */
@Serdeable
public record AudienceReconcileResponse(int revision, boolean changed, @Nullable List<RoomUser> list) {}
