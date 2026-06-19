package com.jilali.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliProperties;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One upstream LiveHub WebSocket + {@link Sinks.Many} event broadcaster per room cname.
 *
 * <p>Lifecycle: created on first subscriber, torn down on last, auto-reconnects with
 * exponential backoff while subscribers remain.  Uses virtual threads for blocking reconnect
 * sleep.  Error handling is done via the {@link java.util.concurrent.CompletableFuture} returned
 * by {@link HtLiveHubUpstreamConnector#connect(String, String, boolean)} — this ensures that
 * stale callbacks from previous connection attempts for the same cname cannot affect the
 * current connector.
 */
@Singleton
public class RoomRealtimeRegistry implements RoomEventSource {

    private static final Logger log = LoggerFactory.getLogger(RoomRealtimeRegistry.class);
    private static final int BACKPRESSURE_LIMIT = 256;

    private final ObjectMapper om;
    private final HtNotifyMapper mapper;
    private final String userId;
    private final RoomSubscriberTracker tracker = new RoomSubscriberTracker();
    private final Map<String, RoomContext> rooms = new ConcurrentHashMap<>();

    public RoomRealtimeRegistry(ObjectMapper om, HtNotifyMapper mapper, JilaliProperties properties) {
        this.om = om;
        this.mapper = mapper;
        this.userId = resolveUserId(properties.defaultAuthToken(), om);
    }

    public Flux<RoomRealtimeEvent> subscribe(String cname) {
        boolean isFirst = tracker.subscribe(cname);
        RoomContext ctx = rooms.computeIfAbsent(cname, RoomContext::new);
        if (isFirst) {
            connectUpstream(ctx);
        }
        return ctx.sink.asFlux();
    }

    public void unsubscribe(String cname) {
        if (tracker.unsubscribe(cname)) {
            RoomContext ctx = rooms.remove(cname);
            if (ctx != null) {
                closeConnector(ctx);
                log.info("RoomRealtimeRegistry: last subscriber left cname='{}' — upstream closed", cname);
            }
        }
    }

    private void connectUpstream(RoomContext ctx) {
        boolean firstAttempt = ctx.reconnectAttempt == 0;
        ctx.sink.tryEmitNext(firstAttempt
            ? new RoomRealtimeEvent.ConnectionState("connecting")
            : new RoomRealtimeEvent.ConnectionState("reconnecting"));

        HtLiveHubUpstreamConnector connector = new HtLiveHubUpstreamConnector(mapper, om);
        connector.attach(
            event -> ctx.sink.tryEmitNext(event),
            () -> onUpstreamDisconnected(ctx));

        ctx.connector = connector;
        ctx.reconnectAttempt = 0;

        connector.connect(userId, ctx.cname, true)
            .whenComplete((ws, ex) -> {
                if (ex != null) {
                    log.warn("RoomRealtimeRegistry: upstream connect failed cname='{}': {}",
                        ctx.cname, ex.getMessage());
                    onUpstreamDisconnected(ctx);
                }
                // on success: onOpen inside HtLiveHubUpstreamConnector already logged "connected"
            });

        ctx.sink.tryEmitNext(new RoomRealtimeEvent.ConnectionState("connected"));
        log.info("RoomRealtimeRegistry: connected upstream for cname='{}'", ctx.cname);
    }

    private void onUpstreamDisconnected(RoomContext ctx) {
        ctx.sink.tryEmitNext(new RoomRealtimeEvent.ConnectionState("disconnected"));
        closeConnector(ctx);

        if (tracker.subscriberCount(ctx.cname) <= 0) {
            return; // nobody watching — unsubscribe() cleanup already ran or is about to
        }

        int attempt = ctx.reconnectAttempt++;
        long delayMs = ReconnectBackoff.delayMillis(attempt);
        log.info("RoomRealtimeRegistry: reconnecting cname='{}' in {}ms (attempt {})",
            ctx.cname, delayMs, attempt);

        Thread.ofVirtual()
            .name("livehub-reconnect-" + ctx.cname)
            .start(() -> {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
                connectUpstream(ctx);
            });
    }

    private void closeConnector(RoomContext ctx) {
        HtLiveHubUpstreamConnector c = ctx.connector;
        ctx.connector = null;
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                log.warn("Error closing LiveHub upstream for cname='{}': {}", ctx.cname, e.getMessage());
            }
        }
    }

    /**
     * Decodes the shared account's uid claim from the configured JWT auth token.
     * Package-visible for {@link RoomRealtimeRegistryTest}.
     */
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

    // ── Inner types ─────────────────────────────────────────────────────────

    private static final class RoomContext {
        final String cname;
        final Sinks.Many<RoomRealtimeEvent> sink =
            Sinks.many().multicast().onBackpressureBuffer(BACKPRESSURE_LIMIT);
        volatile HtLiveHubUpstreamConnector connector;
        volatile int reconnectAttempt = 0;

        RoomContext(String cname) {
            this.cname = cname;
        }
    }
}
