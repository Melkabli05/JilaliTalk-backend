package com.jilali.roomcontext.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Body of {@code POST /api/v2/users/rooms/{cname}/kick} — synthesized from the upstream
 * room_kick payload (see RoomRealtimeEvent.RoomKick). The frontend sends this when a host
 * or moderator removes an audience user from the room entirely (not just the stage).
 *
 * <p>The upstream HelloTalk service has no /room/kick endpoint (re_output/FINDINGS.md §7.5
 * confirms only stage-kick exists upstream). The BFF synthesizes the room_kick event from
 * the existing RoomEventSource.emitSynthetic channel, which means the kick is local-only:
 * other BFF instances behind a load balancer won't see it. Single-instance for now; if
 * multi-instance kick becomes a requirement, the emission needs to be broadcast over
 * the same BFF-to-BFF pubsub the cleanup-jobs leader-lock uses.
 */
@Introspected
@Serdeable
public record RoomKickRequest(
    long userId,
    String nickname,
    String managerName
) {}
