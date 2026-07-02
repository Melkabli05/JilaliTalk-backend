package com.jilali.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliProperties;
import com.jilali.core.UidExtractor;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Pub-sub bridge: browser tabs subscribe per room, first subscriber opens the LiveHub upstream, last leaves closes it. */
@Singleton
public class RoomEventSource {

    private static final Logger log = LoggerFactory.getLogger(RoomEventSource.class);

    private final HtNotifyMapper mapper;
    private final ObjectMapper om;
    private final String connectorUserId;
    private final Map<String, HtLiveHubUpstreamConnector> connectors = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<RoomRealtimeEvent>> sinks = new ConcurrentHashMap<>();
    /** Per-room audience roster revision — incremented on every event that changes the roster so clients can skip unnecessary refetches. */
    private final Map<String, AtomicInteger> audienceRevisions = new ConcurrentHashMap<>();

    public RoomEventSource(HtNotifyMapper mapper, JilaliProperties properties, ObjectMapper om) {
        this.mapper = mapper;
        this.om = om;
        this.connectorUserId = UidExtractor.uidAsString(properties.defaultAuthToken(), om);
        log.info("RoomEventSource: connector userId={}", connectorUserId);
    }

    public Flux<RoomRealtimeEvent> subscribe(String cname) {
        boolean first = counts.computeIfAbsent(cname, k -> new AtomicInteger(0))
            .incrementAndGet() == 1;

        if (first) {
            HtLiveHubUpstreamConnector upstream = new HtLiveHubUpstreamConnector(mapper, om);
            connectors.put(cname, upstream);

            Sinks.Many<RoomRealtimeEvent> sink = sinkFor(cname);
            upstream.attach(
                event -> emitWithRevisionBump(cname, event),
                () -> {
                    log.info("RoomEventSource: upstream disconnected for cname='{}'", cname);
                    sink.tryEmitComplete();
                }
            );

            upstream.connect(connectorUserId, cname, true)
                .exceptionally(ex -> {
                    log.error("RoomEventSource: upstream connection failed for cname='{}': {}", cname, ex.getMessage());
                    sink.tryEmitNext(new RoomRealtimeEvent.Error("Upstream connection failed: " + ex.getMessage()));
                    sink.tryEmitComplete();
                    return null;
                });

            log.info("RoomEventSource: opened upstream for cname='{}'", cname);
        }

        return sinkFor(cname).asFlux();
    }

    public void unsubscribe(String cname) {
        AtomicInteger count = counts.get(cname);
        if (count == null) return;
        int remaining = count.decrementAndGet();
        if (remaining <= 0) {
            counts.remove(cname, count);
            log.info("RoomEventSource: last subscriber left cname='{}', closing upstream", cname);
            HtLiveHubUpstreamConnector upstream = connectors.remove(cname);
            if (upstream != null) upstream.close();
            Sinks.Many<RoomRealtimeEvent> sink = sinks.remove(cname);
            if (sink != null) sink.tryEmitComplete();
        }
    }

    private Sinks.Many<RoomRealtimeEvent> sinkFor(String cname) {
        return sinks.computeIfAbsent(cname, k ->
            Sinks.many().multicast().directBestEffort()
        );
    }

    /**
     * Returns the current audience roster revision for a room. The revision increments on every
     * event that changes the audience roster (user_join, user_quit, stage_join, room_kick).
     * Returns 0 if the room has no active subscribers.
     */
    public int audienceRevision(String cname) {
        AtomicInteger rev = audienceRevisions.get(cname);
        return rev == null ? 0 : rev.get();
    }

    private void bumpAudienceRevision(String cname) {
        audienceRevisions.computeIfAbsent(cname, k -> new AtomicInteger(0)).incrementAndGet();
    }

    private void emitWithRevisionBump(String cname, RoomRealtimeEvent event) {
        if (event instanceof RoomRealtimeEvent.UserJoin
                || event instanceof RoomRealtimeEvent.UserQuit
                || event instanceof RoomRealtimeEvent.StageJoin
                || event instanceof RoomRealtimeEvent.RoomKick) {
            bumpAudienceRevision(cname);
        }
        sinkFor(cname).tryEmitNext(event);
    }
}
