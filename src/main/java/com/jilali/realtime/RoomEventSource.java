package com.jilali.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pub-sub bridge between browser WebSocket sessions and the LiveHub upstream per room.
 *
 * <p>Each room ({@code cname}) owns its own {@link HtLiveHubUpstreamConnector} instance, so
 * connection state (cname, userId, ws, heartbeat thread) is isolated and cannot bleed between
 * rooms. The first subscriber for a room opens the upstream; the last subscriber to leave closes
 * it and removes the sink.
 */
@Singleton
public class RoomEventSource {

    private static final Logger log = LoggerFactory.getLogger(RoomEventSource.class);

    private final HtNotifyMapper mapper;
    private final ObjectMapper om;
    private final RoomSubscriberTracker tracker;

    /** cname → per-room connector. One connector per live upstream connection. */
    private final Map<String, HtLiveHubUpstreamConnector> connectors = new ConcurrentHashMap<>();

    /** cname → per-room sink. */
    private final Map<String, Sinks.Many<RoomRealtimeEvent>> sinks = new ConcurrentHashMap<>();

    public RoomEventSource(HtNotifyMapper mapper, ObjectMapper om) {
        this.mapper = mapper;
        this.om = om;
        this.tracker = new RoomSubscriberTracker();
    }

    /**
     * Subscribe to events for room {@code cname}.
     *
     * @return a {@link Flux} that emits {@link RoomRealtimeEvent}s for this room.
     */
    public Flux<RoomRealtimeEvent> subscribe(String cname, long hostId, int busiType, long heartbeatSeconds) {
        boolean first = tracker.subscribe(cname);

        if (first) {
            HtLiveHubUpstreamConnector upstream = new HtLiveHubUpstreamConnector(mapper, om);
            connectors.put(cname, upstream);

            upstream.attach(
                event -> sinkFor(cname).tryEmitNext(event),
                () -> {
                    log.info("RoomEventSource: upstream disconnected for cname='{}'", cname);
                    sinkFor(cname).tryEmitComplete();
                }
            );

            // Wire connection failure so subscribers get an error event instead of silence.
            upstream.connect("0", cname, true)
                .exceptionally(ex -> {
                    log.error("RoomEventSource: upstream connection failed for cname='{}': {}", cname, ex.getMessage());
                    sinkFor(cname).tryEmitNext(new RoomRealtimeEvent.Error("Upstream connection failed: " + ex.getMessage()));
                    sinkFor(cname).tryEmitComplete();
                    return null;
                });

            log.info("RoomEventSource: opened upstream for cname='{}'", cname);
        }

        return sinkFor(cname).asFlux();
    }

    /**
     * Called by {@link RoomSocketController} when a WebSocket session closes, so the per-room
     * subscriber count is decremented and the upstream closed when the last subscriber leaves.
     */
    public void unsubscribe(String cname) {
        boolean last = tracker.unsubscribe(cname);
        if (last) {
            log.info("RoomEventSource: last subscriber left cname='{}', closing upstream", cname);
            HtLiveHubUpstreamConnector upstream = connectors.remove(cname);
            if (upstream != null) {
                upstream.close();
            }
            Sinks.Many<RoomRealtimeEvent> sink = sinks.remove(cname);
            if (sink != null) {
                sink.tryEmitComplete();
            }
        }
    }

    private Sinks.Many<RoomRealtimeEvent> sinkFor(String cname) {
        return sinks.computeIfAbsent(cname, k ->
            Sinks.many().multicast().directBestEffort()
        );
    }
}
