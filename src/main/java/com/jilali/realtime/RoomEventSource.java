package com.jilali.realtime;

import com.jilali.realtime.dto.RoomRealtimeEvent;
import reactor.core.publisher.Flux;

/**
 * The room-event subscription surface {@link RoomSocketController} depends on. Exists
 * separately from {@link RoomRealtimeRegistry} so tests can substitute a fake via
 * {@code @Replaces(RoomRealtimeRegistry.class)} without touching the real LiveHub socket.
 */
public interface RoomEventSource {
    Flux<RoomRealtimeEvent> subscribe(String cname);
    void unsubscribe(String cname);
}