package com.jilali.room.dto;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Returns the current audience roster revision for a room — clients poll this endpoint to decide
 * whether to refetch the audience list, rather than blindly fetching on a fixed timer.
 *
 * @param revision  monotonic counter incremented on every roster-affecting event (user_join,
 *                 user_quit, stage_join, room_kick). A client that last fetched with revision N
 *                 should only refetch when revision > N. A value of 0 means the room has no active
 *                 WebSocket subscribers on the BFF.
 */
@Serdeable
public record AudienceRevisionResponse(int revision) {}
