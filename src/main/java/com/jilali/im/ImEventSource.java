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
 */
@Singleton
public class ImEventSource {

    private static final Logger log = LoggerFactory.getLogger(ImEventSource.class);

    private final long userId;
    private final String jwt;
    private final String deviceId;
    private final String deviceModel;
    private final ObjectMapper om;

    private final AtomicInteger subscriberCount = new AtomicInteger(0);
    private volatile HtImUpstreamConnector connector;
    private volatile Sinks.Many<ImRealtimeEvent> sink;

    public ImEventSource(JilaliProperties properties, ObjectMapper om) {
        this.jwt         = properties.defaultAuthToken();
        this.deviceId    = properties.deviceId();
        this.deviceModel = properties.deviceModel();
        this.om          = om;
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
                event -> newSink.tryEmitNext(event),
                () -> {
                    log.info("ImEventSource: upstream disconnected");
                    newSink.tryEmitComplete();
                }
            );

            newSink.tryEmitNext(new ImRealtimeEvent.ConnectionState("connecting"));

            upstream.connect().exceptionally(ex -> {
                log.error("ImEventSource: upstream connection failed: {}", ex.getMessage());
                newSink.tryEmitNext(new ImRealtimeEvent.Error("IM upstream connection failed: " + ex.getMessage()));
                newSink.tryEmitComplete();
                return null;
            });

            log.info("ImEventSource: opened upstream uid={}", userId);
        }

        return sink.asFlux();
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
        }
    }

}
