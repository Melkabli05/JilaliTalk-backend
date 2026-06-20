package com.jilali.realtime;

import com.jilali.realtime.dto.RoomRealtimeEvent;
import reactor.core.publisher.Flux;

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
     */
    Flux<RoomRealtimeEvent> subscribe(String cname, long hostId, int busiType, long heartbeatSeconds);

    void unsubscribe(String cname);
}