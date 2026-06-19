package com.jilali.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliProperties;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import io.micronaut.websocket.WebSocketClient;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Owns the lifecycle of one {@link HtLiveHubUpstreamConnector} + one event broadcaster per
 * active room {@code cname} — created on the first {@link RoomSocketController} subscriber,
 * disposed on the last, reconnected with backoff if the upstream connection drops while
 * subscribers remain.
 */
@Singleton
public class RoomRealtimeRegistry {

    private static final Logger log = LoggerFactory.getLogger(RoomRealtimeRegistry.class);
    private static final String LIVEHUB_URL = "wss://uploadprocn.hellotalk8.com/livehub/ws/conn";

    private final WebSocketClient webSocketClient;
    private final ObjectMapper om;
    private final String userId;
    private final RoomSubscriberTracker tracker = new RoomSubscriberTracker();
    private final Map<String, RoomContext> rooms = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reconnectScheduler =
        Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("livehub-reconnect").factory());

    public RoomRealtimeRegistry(WebSocketClient webSocketClient, ObjectMapper om, JilaliProperties properties) {
        this.webSocketClient = webSocketClient;
        this.om = om;
        this.userId = resolveUserId(properties.defaultAuthToken(), om);
    }

    /** Subscribes to {@code cname}'s event stream, connecting upstream if this is the first subscriber. */
    public Flux<RoomRealtimeEvent> subscribe(String cname) {
        boolean isFirst = tracker.subscribe(cname);
        RoomContext context = rooms.computeIfAbsent(cname, RoomContext::new);
        if (isFirst) {
            connectUpstream(context);
        }
        return context.sink.asFlux();
    }

    /** Unsubscribes from {@code cname}; disconnects upstream if this was the last subscriber. */
    public void unsubscribe(String cname) {
        boolean isLast = tracker.unsubscribe(cname);
        if (isLast) {
            RoomContext context = rooms.remove(cname);
            if (context != null) closeConnector(context);
            log.info("RoomRealtimeRegistry: last subscriber left cname='{}' — upstream closed", cname);
        }
    }

    private void connectUpstream(RoomContext context) {
        String url = LIVEHUB_URL + "?user_id=" + enc(userId) + "&cname=" + enc(context.cname) + "&is_visitor=true";
        context.sink.tryEmitNext(new RoomRealtimeEvent.ConnectionState(context.reconnectAttempt == 0 ? "connecting" : "reconnecting"));
        Mono.from(webSocketClient.connect(HtLiveHubUpstreamConnector.class, URI.create(url)))
            .subscribe(
                connector -> {
                    context.connector = connector;
                    context.reconnectAttempt = 0;
                    connector.attach(
                        event -> context.sink.tryEmitNext(event),
                        () -> onUpstreamDisconnected(context));
                    context.sink.tryEmitNext(new RoomRealtimeEvent.ConnectionState("connected"));
                    log.info("RoomRealtimeRegistry: connected upstream for cname='{}'", context.cname);
                },
                error -> {
                    log.error("RoomRealtimeRegistry: upstream connect failed for cname='{}': {}", context.cname, error.getMessage());
                    onUpstreamDisconnected(context);
                });
    }

    private void onUpstreamDisconnected(RoomContext context) {
        context.sink.tryEmitNext(new RoomRealtimeEvent.ConnectionState("disconnected"));
        if (tracker.subscriberCount(context.cname) <= 0) {
            return; // nobody is watching this room anymore — unsubscribe()'s cleanup already ran or is about to
        }
        int attempt = context.reconnectAttempt++;
        long delayMs = ReconnectBackoff.delayMillis(attempt);
        log.info("RoomRealtimeRegistry: reconnecting cname='{}' in {}ms (attempt {})", context.cname, delayMs, attempt);
        reconnectScheduler.schedule(() -> connectUpstream(context), delayMs, TimeUnit.MILLISECONDS);
    }

    private void closeConnector(RoomContext context) {
        HtLiveHubUpstreamConnector connector = context.connector;
        if (connector != null) {
            try {
                connector.close();
            } catch (Exception e) {
                log.warn("Error closing LiveHub upstream for cname='{}': {}", context.cname, e.getMessage());
            }
        }
    }

    /** Package-visible for {@link RoomRealtimeRegistryTest}. Decodes the shared account's {@code uid} claim. */
    static String resolveUserId(String token, ObjectMapper om) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IllegalStateException("Configured jilali.default-auth-token is not a JWT");
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = om.readTree(payloadBytes);
            JsonNode uid = payload.get("uid");
            if (uid == null || uid.isNull()) {
                throw new IllegalStateException("JWT payload has no 'uid' claim");
            }
            return uid.asText();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not decode 'uid' from jilali.default-auth-token", e);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static final class RoomContext {
        final String cname;
        final Sinks.Many<RoomRealtimeEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        volatile HtLiveHubUpstreamConnector connector;
        volatile int reconnectAttempt = 0;

        RoomContext(String cname) {
            this.cname = cname;
        }
    }
}