package com.jilali.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
 * <p>Each {@link #subscribe} call registers one browser tab as a subscriber for a room. The first
 * subscriber for a room opens the LiveHub upstream connection; the last subscriber to leave closes it.
 * All subscriber tabs for the same room receive the same events forwarded from the single upstream.
 */
@Singleton
public class RoomEventSource {

    private static final Logger log = LoggerFactory.getLogger(RoomEventSource.class);

    private final HtLiveHubUpstreamConnector upstream;
    private final RoomSubscriberTracker tracker;
    private final ObjectMapper om;

    /** cname → per-room sink. One sink per live upstream connection. */
    private final Map<String, Sinks.Many<RoomRealtimeEvent>> sinks = new ConcurrentHashMap<>();

    public RoomEventSource(HtNotifyMapper mapper, ObjectMapper om) {
        this.upstream = new HtLiveHubUpstreamConnector(mapper, om);
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
            upstream.attach(
                event -> sinkFor(cname).tryEmitNext(event),
                () -> {
                    log.info("RoomEventSource: upstream disconnected for cname='{}'", cname);
                    sinkFor(cname).tryEmitComplete();
                }
            );
            upstream.connect("0", cname, true);
            log.info("RoomEventSource: opened upstream for cname='{}'", cname);
        }

        return sinkFor(cname).asFlux();
    }

    private Sinks.Many<RoomRealtimeEvent> sinkFor(String cname) {
        return sinks.computeIfAbsent(cname, k -> {
            Sinks.Many<RoomRealtimeEvent> sink = Sinks.many().multicast().directBestEffort();
            return sink;
        });
    }
}
