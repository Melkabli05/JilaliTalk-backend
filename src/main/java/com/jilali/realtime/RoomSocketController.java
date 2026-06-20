package com.jilali.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliProperties;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import io.micronaut.http.HttpRequest;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Push-only bridge: browser tabs subscribe here per room {@code cname} and receive every
 * {@link RoomRealtimeEvent} {@link RoomEventSource} publishes for that room. Frontend
 * actions (comment, kick, mute, ...) go through the existing REST controllers, never
 * through this socket — see the design spec's "WebSocket is push-only" decision.
 */
@Singleton
@ServerWebSocket("/ws/ht/{cname}")
public class RoomSocketController {

    private static final Logger log = LoggerFactory.getLogger(RoomSocketController.class);

    private final RoomEventSource source;
    private final ObjectMapper om;
    private final Set<String> allowedOrigins;
    private final ConcurrentHashMap<String, Disposable> subscriptions = new ConcurrentHashMap<>();

    public RoomSocketController(RoomEventSource source, ObjectMapper om, JilaliProperties properties) {
        this.source = source;
        this.om = om;
        this.allowedOrigins = Set.copyOf(properties.allowedWebSocketOrigins());
    }

    @OnOpen
    public void onOpen(String cname, HttpRequest<?> request, WebSocketSession session) {
        String origin = request.getHeaders().get("Origin");
        if (origin != null && !allowedOrigins.contains(origin)) {
            log.warn("RoomSocketController: rejecting connection from disallowed origin '{}'", origin);
            session.close();
            return;
        }
        // Optional query params drive the room-level presence heartbeat (see
        // RoomRealtimeRegistry) — absent or 0 means "don't drive it from this subscriber"
        // (e.g. an invisible/ghost join), mirroring the frontend's old visible-only guard.
        var params = request.getParameters();
        long hostId = params.get("hostId", Long.class).orElse(0L);
        int busiType = params.get("busiType", Integer.class).orElse(2);
        long heartbeatSeconds = params.get("heartbeatSeconds", Long.class).orElse(0L);

        Disposable subscription = source.subscribe(cname, hostId, busiType, heartbeatSeconds)
            .subscribe(event -> sendEvent(session, event));
        subscriptions.put(session.getId(), subscription);
        log.info("RoomSocketController: session '{}' subscribed to cname='{}'", session.getId(), cname);
    }

    /** No-op — this endpoint is push-only, but Micronaut requires an @OnMessage handler to register the route. */
    @OnMessage
    public void onMessage(String cname, String message) {
        log.trace("RoomSocketController: ignoring inbound frontend message cname='{}'", cname);
    }

    @OnClose
    public void onClose(String cname, WebSocketSession session) {
        Disposable subscription = subscriptions.remove(session.getId());
        if (subscription != null) subscription.dispose();
        source.unsubscribe(cname);
        log.info("RoomSocketController: session '{}' unsubscribed from cname='{}'", session.getId(), cname);
    }

    private void sendEvent(WebSocketSession session, RoomRealtimeEvent event) {
        if (!session.isOpen()) return;
        try {
            session.sendAsync(om.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("RoomSocketController: failed to serialize event for session '{}': {}", session.getId(), e.getMessage());
        }
    }
}