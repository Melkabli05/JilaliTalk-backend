package com.jilali.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.auth.AuthController;
import com.jilali.auth.SessionRepository;
import com.jilali.core.JilaliProperties;
import com.jilali.im.dto.ImEvent;
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
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Push-only relay for the per-user {@code ht_im/sock} connection — one subscription per
 * logged-in JilaliTalk session, opened once at app boot (not per-room, unlike
 * {@code /ws/ht/{cname}}). Authenticated via the same session cookie
 * {@link AuthController} issues; a request with no valid session, or a session with no
 * HelloTalk JWT assigned yet, gets a clean close instead of a stream — this account-level
 * feed being unavailable is the expected, decoupled-for-now state, not an error.
 */
@Singleton
@ServerWebSocket("/ws/im")
public class ImSocketController {

    private static final Logger log = LoggerFactory.getLogger(ImSocketController.class);

    private final ImConnectionRegistry registry;
    private final SessionRepository sessions;
    private final ObjectMapper om;
    private final Set<String> allowedOrigins;
    private final ConcurrentHashMap<String, Disposable> subscriptions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> sessionUserIds = new ConcurrentHashMap<>();

    public ImSocketController(ImConnectionRegistry registry, SessionRepository sessions, ObjectMapper om,
                               JilaliProperties properties) {
        this.registry = registry;
        this.sessions = sessions;
        this.om = om;
        this.allowedOrigins = Set.copyOf(properties.allowedWebSocketOrigins());
    }

    @OnOpen
    public void onOpen(HttpRequest<?> request, WebSocketSession session) {
        String origin = request.getHeaders().get("Origin");
        if (origin != null && !allowedOrigins.contains(origin)) {
            log.warn("ImSocketController: rejecting connection from disallowed origin '{}'", origin);
            session.close();
            return;
        }

        Optional<Long> jilaliUserId = request.getCookies().findCookie(AuthController.SESSION_COOKIE)
            .flatMap(cookie -> sessions.resolveUserId(cookie.getValue()));

        if (jilaliUserId.isEmpty()) {
            log.info("ImSocketController: no valid session — closing");
            session.close();
            return;
        }

        Optional<Flux<ImEvent>> stream = registry.subscribe(jilaliUserId.get());
        if (stream.isEmpty()) {
            log.info("ImSocketController: jilaliUserId={} has no HelloTalk token assigned — closing",
                jilaliUserId.get());
            session.close();
            return;
        }

        sessionUserIds.put(session.getId(), jilaliUserId.get());
        Disposable subscription = stream.get().subscribe(event -> sendEvent(session, event));
        subscriptions.put(session.getId(), subscription);
        log.info("ImSocketController: session '{}' subscribed jilaliUserId={}", session.getId(), jilaliUserId.get());
    }

    /** No-op — this endpoint is push-only, but Micronaut requires an @OnMessage handler to register the route. */
    @OnMessage
    public void onMessage(String message) {
        log.trace("ImSocketController: ignoring inbound frontend message");
    }

    @OnClose
    public void onClose(WebSocketSession session) {
        Disposable subscription = subscriptions.remove(session.getId());
        if (subscription != null) subscription.dispose();
        Long jilaliUserId = sessionUserIds.remove(session.getId());
        if (jilaliUserId != null) {
            registry.unsubscribe(jilaliUserId);
            log.info("ImSocketController: session '{}' unsubscribed jilaliUserId={}", session.getId(), jilaliUserId);
        }
    }

    private void sendEvent(WebSocketSession session, ImEvent event) {
        if (!session.isOpen()) return;
        try {
            session.sendAsync(om.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("ImSocketController: failed to serialize event for session '{}': {}", session.getId(), e.getMessage());
        }
    }
}
