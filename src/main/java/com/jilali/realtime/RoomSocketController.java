package com.jilali.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliProperties;
import com.jilali.realtime.dto.RoomCcRealtimeEvent;
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
 * Push-only WebSocket bridge: browser tabs subscribe per room {@code cname} and receive every
 * {@link RoomRealtimeEvent}. Frontend actions go through REST controllers, never here.
 *
 * <p>Clients opt into the AI-captioning / subtitle stream by appending {@code ?cc=1} to the
 * WebSocket URL. Each session's room-channel subscription and CC-channel subscription are
 * tracked independently so {@link #onClose} can dispose both.
 */
@Singleton
@ServerWebSocket("/ws/ht/{cname}")
public class RoomSocketController {

    private static final Logger log = LoggerFactory.getLogger(RoomSocketController.class);

    private final RoomEventSource source;
    private final ObjectMapper om;
    private final Set<String> allowedOrigins;
    private final ConcurrentHashMap<String, Disposable> subscriptions = new ConcurrentHashMap<>();
    /** Tracks each session's CC subscription so {@link #onClose} can dispose + unsubscribe it
     *  without affecting the session's main room-channel subscription. */
    private final ConcurrentHashMap<String, Disposable> ccSubscriptions = new ConcurrentHashMap<>();

    public RoomSocketController(RoomEventSource source, ObjectMapper om, JilaliProperties properties) {
        this.source = source;
        this.om = om;
        this.allowedOrigins = Set.copyOf(properties.allowedWebSocketOrigins());
    }

    @OnOpen
    public void onOpen(String cname, HttpRequest<?> request, WebSocketSession session) {
        String origin = request.getHeaders().get("Origin");
        if (origin != null && !allowedOrigins.contains(origin)) {
            log.warn("RoomSocketController: rejecting disallowed origin '{}'", origin);
            session.close();
            return;
        }

        String sessionId = session.getId();
        Disposable subscription = source.subscribe(cname)
            .doOnComplete(() -> { if (session.isOpen()) session.close(); })
            .subscribe(event -> sendEvent(session, event));
        subscriptions.put(sessionId, subscription);

        // CC stream is opt-in per session: subscribe if the query string carried ?cc=1.
        // Frontend toggles this on when the user enables subtitles and off when they disable.
        boolean wantCc = false;
        String ccParam = request.getParameters().get("cc");
        if (ccParam != null && (ccParam.equals("1") || ccParam.equalsIgnoreCase("true"))) {
            wantCc = true;
        }
        if (wantCc) {
            Disposable cc = source.subscribeCc(cname)
                .subscribe(event -> sendCcEvent(session, event));
            ccSubscriptions.put(sessionId, cc);
        }

        log.info("RoomSocketController: session '{}' subscribed to cname='{}' (cc={})",
            sessionId, cname, wantCc);
    }

    /** No-op — this endpoint is push-only, but Micronaut requires an @OnMessage handler. */
    @OnMessage
    public void onMessage(String cname, String message) {
        log.trace("RoomSocketController: ignoring inbound message cname='{}'", cname);
    }

    @OnClose
    public void onClose(String cname, WebSocketSession session) {
        String sessionId = session.getId();
        Disposable subscription = subscriptions.remove(sessionId);
        if (subscription != null) subscription.dispose();
        Disposable cc = ccSubscriptions.remove(sessionId);
        if (cc != null) {
            cc.dispose();
            source.unsubscribeCc(cname);
        }
        source.unsubscribe(cname);
        log.info("RoomSocketController: session '{}' unsubscribed from cname='{}'", sessionId, cname);
    }

    private void sendEvent(WebSocketSession session, RoomRealtimeEvent event) {
        if (!session.isOpen()) return;
        try {
            session.sendAsync(om.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("RoomSocketController: failed to serialize event for session '{}': {}",
                session.getId(), e.getMessage());
        }
    }

    private void sendCcEvent(WebSocketSession session, RoomCcRealtimeEvent event) {
        if (!session.isOpen()) return;
        try {
            session.sendAsync(om.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("RoomSocketController: failed to serialize CC event for session '{}': {}",
                session.getId(), e.getMessage());
        }
    }
}
