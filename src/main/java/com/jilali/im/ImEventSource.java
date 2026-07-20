package com.jilali.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliProperties;
import com.jilali.core.UidExtractor;
import com.jilali.im.dto.ImRealtimeEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Singleton pub-sub bridge for the IM binary WebSocket channel.
 * The first browser session to subscribe opens the upstream connection; the last to
 * leave closes it. The IM channel is global (not per-room), so there is at most one
 * upstream connection at any time.
 *
 * <p>Events flow: connector -> {@link ImEventEnricher} (fills in missing nickname/avatar
 * from {@code JilaliGateway.userInfo()}) -> sink. Enrichment runs after the raw mapper so
 * the frontend never sees a profile_visit event whose only identity field is the raw
 * numeric visitorUserId.
 */
@Singleton
public class ImEventSource {

    private static final Logger log = LoggerFactory.getLogger(ImEventSource.class);

    private final long userId;
    private final String jwt;
    private final String deviceId;
    private final String deviceModel;
    private final ObjectMapper om;
    private final ImEventEnricher enricher;

    private final AtomicInteger subscriberCount = new AtomicInteger(0);
    private volatile HtImUpstreamConnector connector;
    private volatile Sinks.Many<ImRealtimeEvent> sink;
    /** Last connection-state event seen, so a subscriber joining after the upstream is already
     *  connected (e.g. a second browser tab) gets the current state immediately instead of
     *  waiting for a state transition that already happened before it subscribed. */
    private volatile ImRealtimeEvent.ConnectionState lastConnectionState =
        new ImRealtimeEvent.ConnectionState("disconnected");

    public ImEventSource(JilaliProperties properties, ObjectMapper om, ImEventEnricher enricher) {
        this.jwt         = properties.defaultAuthToken();
        this.deviceId    = properties.deviceId();
        this.deviceModel = properties.deviceModel();
        this.om          = om;
        this.enricher    = enricher;
        this.userId      = UidExtractor.uidAsLong(jwt, om);
        log.info("ImEventSource: userId={}", userId);
    }

    public Flux<ImRealtimeEvent> subscribe() {
        boolean first = subscriberCount.incrementAndGet() == 1;
        if (first) {
            Sinks.Many<ImRealtimeEvent> newSink = Sinks.many().multicast().directBestEffort();
            this.sink = newSink;

            HtImUpstreamConnector upstream = new HtImUpstreamConnector(userId, jwt, deviceId, deviceModel, om);
            this.connector = upstream;

            upstream.attach(
                // ImEventEnricher.enrich() never errors — it falls back to emitting the raw event
                // on lookup failure, so the wire always sees exactly one event per upstream push.
                event -> enricher.enrich(event).subscribe(this::emitAndTrackState),
                () -> {
                    log.info("ImEventSource: upstream disconnected");
                    newSink.tryEmitComplete();
                }
            );

            emitAndTrackState(new ImRealtimeEvent.ConnectionState("connecting"));

            upstream.connect().exceptionally(ex -> {
                log.error("ImEventSource: upstream connection failed: {}", ex.getMessage());
                newSink.tryEmitNext(new ImRealtimeEvent.Error("IM upstream connection failed: " + ex.getMessage()));
                newSink.tryEmitComplete();
                return null;
            });

            log.info("ImEventSource: opened upstream uid={}", userId);
            return sink.asFlux();
        }

        return Flux.concat(Flux.just(lastConnectionState), sink.asFlux());
    }

    private void emitAndTrackState(ImRealtimeEvent event) {
        if (event instanceof ImRealtimeEvent.ConnectionState cs) {
            lastConnectionState = cs;
        }
        Sinks.Many<ImRealtimeEvent> s = sink;
        if (s != null) s.tryEmitNext(event);
    }

    public void unsubscribe() {
        int remaining = subscriberCount.decrementAndGet();
        if (remaining <= 0) {
            subscriberCount.set(0);
            log.info("ImEventSource: last subscriber left, closing upstream");
            HtImUpstreamConnector c = connector;
            if (c != null) { connector = null; c.close(); }
            Sinks.Many<ImRealtimeEvent> s = sink;
            if (s != null) { sink = null; s.tryEmitComplete(); }
            lastConnectionState = new ImRealtimeEvent.ConnectionState("disconnected");
        }
    }

    /**
     * Hand a pre-built packet to the live connector for upstream delivery. Used by IM HTTP endpoints
     * (read-receipt / typing / private-message send) to emit through the same WS, sharing the
     * SequentialSender and the 30-second heartbeat of the IM channel. No-op when the WS is not
     * currently connected (caller doesn't get an exception — typical "send during reconnect window"
     * pattern, retries come from upstream).
     */
    public void sendOutbound(byte[] data) {
        HtImUpstreamConnector c = connector;
        if (c != null) c.sendOutbound(data);
    }

}
