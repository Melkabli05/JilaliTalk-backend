package com.jilali.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliProperties;
import com.jilali.im.dto.ImRealtimeEvent;
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
 * Push-only WebSocket endpoint {@code /ws/im}: relays every {@link ImRealtimeEvent} from
 * the upstream HelloTalk IM binary channel to connected browser sessions.
 */
@Singleton
@ServerWebSocket("/ws/im")
public class ImSocketController {

    private static final Logger log = LoggerFactory.getLogger(ImSocketController.class);

    private final ImEventSource source;
    private final ObjectMapper om;
    private final Set<String> allowedOrigins;
    private final ConcurrentHashMap<String, Disposable> subscriptions = new ConcurrentHashMap<>();

    public ImSocketController(ImEventSource source, ObjectMapper om, JilaliProperties properties) {
        this.source         = source;
        this.om             = om;
        this.allowedOrigins = Set.copyOf(properties.allowedWebSocketOrigins());
    }

    @OnOpen
    public void onOpen(HttpRequest<?> request, WebSocketSession session) {
        String origin = request.getHeaders().get("Origin");
        if (origin != null && !allowedOrigins.contains(origin)) {
            log.warn("ImSocketController: rejecting disallowed origin '{}'", origin);
            session.close();
            return;
        }

        String sessionId = session.getId();
        Disposable sub = source.subscribe()
            .doOnComplete(() -> { if (session.isOpen()) session.close(); })
            .subscribe(event -> sendEvent(session, event));
        subscriptions.put(sessionId, sub);
        log.info("ImSocketController: session '{}' subscribed", sessionId);
    }

    @OnMessage
    public void onMessage(String message) {
        log.trace("ImSocketController: ignoring inbound message");
    }

    @OnClose
    public void onClose(WebSocketSession session) {
        String sessionId = session.getId();
        Disposable sub = subscriptions.remove(sessionId);
        if (sub != null) sub.dispose();
        source.unsubscribe();
        log.info("ImSocketController: session '{}' unsubscribed", sessionId);
    }

    private void sendEvent(WebSocketSession session, ImRealtimeEvent event) {
        if (!session.isOpen()) return;
        try {
            session.sendAsync(om.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("ImSocketController: failed to serialize event for session '{}': {}",
                session.getId(), e.getMessage());
        }
    }
}
