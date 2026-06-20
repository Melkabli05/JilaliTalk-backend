package com.jilali.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.client.JilaliClient;
import com.jilali.core.JilaliProperties;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import com.jilali.user.dto.HeartbeatRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
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
 *
 * <p>Also drives the room's presence heartbeat (one ticker per room, started once any
 * subscriber supplies a usable {@code hostId}) instead of leaving every browser tab to run
 * its own redundant client-side timer — see {@link #subscribe(String, long, int, long)}.
 */
@Singleton
public class RoomRealtimeRegistry implements RoomEventSource {

    private static final Logger log = LoggerFactory.getLogger(RoomRealtimeRegistry.class);
    private static final int BACKPRESSURE_LIMIT = 256;
    private static final long DEFAULT_HEARTBEAT_SECONDS = 55;

    private final ObjectMapper om;
    private final HtNotifyMapper mapper;
    private final JilaliProperties properties;
    private final JilaliClient client;
    private final String userId;
    private final RoomSubscriberTracker tracker = new RoomSubscriberTracker();
    private final Map<String, RoomContext> rooms = new ConcurrentHashMap<>();

    public RoomRealtimeRegistry(ObjectMapper om, HtNotifyMapper mapper, JilaliProperties properties, JilaliClient client) {
        this.om = om;
        this.mapper = mapper;
        this.properties = properties;
        this.client = client;
        this.userId = resolveUserId(properties.defaultAuthToken(), om);
    }

    @Override
    public Flux<RoomRealtimeEvent> subscribe(String cname, long hostId, int busiType, long heartbeatSeconds) {
        boolean isFirst = tracker.subscribe(cname);
        RoomContext ctx = rooms.computeIfAbsent(cname, RoomContext::new);
        if (hostId > 0) {
            ctx.hostId = hostId;
            ctx.busiType = busiType;
            ctx.heartbeatSeconds = heartbeatSeconds > 0 ? heartbeatSeconds : DEFAULT_HEARTBEAT_SECONDS;
        }
        if (isFirst) {
            connectUpstream(ctx);
        }
        maybeStartHeartbeat(ctx);
        return ctx.sink.asFlux();
    }

    public void unsubscribe(String cname) {
        if (tracker.unsubscribe(cname)) {
            RoomContext ctx = rooms.remove(cname);
            if (ctx != null) {
                closeConnector(ctx);
                stopHeartbeat(ctx);
                log.info("RoomRealtimeRegistry: last subscriber left cname='{}' — upstream closed", cname);
            }
        }
    }

    /**
     * Starts the room's heartbeat ticker the first time a subscriber supplies a usable
     * {@code hostId} — which may not be the very first subscriber (an invisible/ghost join
     * carries {@code hostId=0} and is skipped, mirroring the frontend's old
     * visible-only guard). Once running, the ticker keeps going as long as any subscriber
     * remains, even if that original subscriber later disconnects.
     */
    private void maybeStartHeartbeat(RoomContext ctx) {
        if (ctx.hostId <= 0 || ctx.heartbeatThread != null) {
            return;
        }
        ctx.heartbeatThread = Thread.ofVirtual()
            .name("heartbeat-" + ctx.cname)
            .start(() -> runHeartbeatLoop(ctx));
    }

    private void runHeartbeatLoop(RoomContext ctx) {
        while (tracker.subscriberCount(ctx.cname) > 0) {
            try {
                client.heartbeat(new HeartbeatRequest(ctx.hostId, false, ctx.busiType, ctx.cname));
            } catch (Exception e) {
                log.warn("RoomRealtimeRegistry: heartbeat failed cname='{}': {}", ctx.cname, e.getMessage());
            }
            try {
                Thread.sleep(Duration.ofSeconds(ctx.heartbeatSeconds));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void stopHeartbeat(RoomContext ctx) {
        Thread t = ctx.heartbeatThread;
        ctx.heartbeatThread = null;
        if (t != null) {
            t.interrupt();
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
                    return;
                }
                // Connection established — emit "connected" only after the CF confirms success.
                ctx.sink.tryEmitNext(new RoomRealtimeEvent.ConnectionState("connected"));
                log.info("RoomRealtimeRegistry: connected upstream for cname='{}'", ctx.cname);
            });
    }

    private void onUpstreamDisconnected(RoomContext ctx) {
        if (ctx.reconnecting) {
            return; // Reconnect already scheduled — guard against double-scheduling.
        }
        ctx.reconnecting = true;

        ctx.sink.tryEmitNext(new RoomRealtimeEvent.ConnectionState("disconnected"));
        closeConnector(ctx);

        if (tracker.subscriberCount(ctx.cname) <= 0) {
            return;
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
                ctx.reconnecting = false;
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
        /** Guard against concurrent reconnect scheduling from overlapping disconnect callbacks. */
        volatile boolean reconnecting = false;

        // ── Heartbeat state — set once a subscriber supplies a usable hostId ──
        volatile long hostId = 0;
        volatile int busiType = 2;
        volatile long heartbeatSeconds = DEFAULT_HEARTBEAT_SECONDS;
        volatile Thread heartbeatThread;

        RoomContext(String cname) {
            this.cname = cname;
        }
    }
}
