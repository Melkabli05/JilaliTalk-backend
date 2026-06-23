package com.jilali.realtime;

import com.jilali.realtime.dto.RoomRealtimeEvent;
import reactor.core.publisher.Flux;

import java.util.Optional;

/**
 * The room-event subscription surface {@link RoomSocketController} depends on. Exists
 * separately from {@link RoomRealtimeRegistry} so tests can substitute a fake via
 * {@code @Replaces(RoomRealtimeRegistry.class)} without touching the real LiveHub socket.
 */
public interface RoomEventSource {

    /**
     * @param hostId per-room presence heartbeat target; pass {@code 0} for a subscriber that
     *               should receive events but not drive the room's heartbeat (e.g. an
     *               invisible/ghost join, mirroring the frontend's old visible-only guard).
     * @param heartbeatSeconds upstream-configured heartbeat interval, or {@code <= 0} to use
     *               the default.
     * @param jilaliUserId the platform session's JilaliTalk user id, if any — lets the
     *               registry open the LiveHub connection as this viewer's own HelloTalk
     *               identity (when one is assigned in the token pool) instead of always
     *               falling back to the shared default identity. Personally-targeted LiveHub
     *               events (stage invite, mod invite) are only ever delivered to the
     *               connection matching the real recipient's own uid — see
     *               RoomRealtimeRegistry's class doc.
     */
    Flux<RoomRealtimeEvent> subscribe(String cname, long hostId, int busiType, long heartbeatSeconds,
                                       Optional<Long> jilaliUserId);

    void unsubscribe(String cname, Optional<Long> jilaliUserId);
}
