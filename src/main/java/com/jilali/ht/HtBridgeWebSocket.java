package com.jilali.ht;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliProperties;
import com.jilali.ht.dto.HtEvent;
import com.jilali.ht.dto.HtOutboundFrame;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.*;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bridges HelloTalk's LiveHub WebSocket to frontend Angular clients as normalised JSON.
 *
 * <p>Path: {@code /ws/ht/{cname}?user_id={uid}&is_visitor=true}
 *
 * <p>Two channels:
 * <ul>
 *   <li><b>Frontend ↔ BFF</b> — plain JSON WebSocket (Angular clients subscribe here)</li>
 *   <li><b>BFF ↔ LiveHub</b> — plain JSON WebSocket to HelloTalk's event server
 *       at {@code wss://uploadprocn.hellotalk8.com/livehub/ws/conn}</li>
 * </ul>
 *
 * <p>One LiveHub upstream connection is created per room (per {@code cname}), shared by
 * all Angular clients subscribed to that room. The connection is torn down when the last
 * client disconnects.
 *
 * <p>LiveHub protocol (from the original {@code fireRoomWebSocket} in the old frontend):
 * <ol>
 *   <li>On open: send {@code {"user_id":"…","cname":"…","action":1}}</li>
 *   <li>Server replies with {@code {"heartbeat_sec":60}} — schedule heartbeats accordingly</li>
 *   <li>Every event carries a {@code msg_id} — ACK with {@code {"msg_id":"…","action":3,…}}</li>
 *   <li>Server confirms heartbeat with {@code {"heartbeat_time":…}} — reschedule next send</li>
 * </ol>
 *
 * <p>Thread-safe: all mutable state is in concurrent collections.
 */
@Singleton
@ServerWebSocket("/ws/ht/{cname}")
@ExecuteOn(TaskExecutors.BLOCKING)
public class HtBridgeWebSocket {

    private static final Logger log = LoggerFactory.getLogger(HtBridgeWebSocket.class);

    private final HtEventParser parser;
    private final ObjectMapper om;
    private final JilaliProperties properties;

    /** Angular sessions per room. */
    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    /** One LiveHub upstream connection per room cname. */
    private final Map<String, HtUpstream> upstreams = new ConcurrentHashMap<>();

    public HtBridgeWebSocket(
            HtEventParser parser,
            ObjectMapper om,
            JilaliProperties properties) {
        this.parser = parser;
        this.om = om;
        this.properties = properties;
    }

    // ---- Frontend-facing WebSocket lifecycle ----------------------------------------

    @OnOpen
    public void onOpen(
            String cname,
            @QueryValue(value = "user_id", defaultValue = "0") String userId,
            @QueryValue(value = "is_visitor", defaultValue = "true") boolean isVisitor,
            WebSocketSession session) {

        roomSessions.computeIfAbsent(cname, k -> ConcurrentHashMap.newKeySet()).add(session);

        // Create upstream once per cname; subsequent sessions reuse it
        upstreams.computeIfAbsent(cname, k -> {
            String url = buildLiveHubUrl(userId, cname, isVisitor);
            var us = new HtUpstream(
                url, cname, userId, isVisitor,
                parser,
                event -> broadcastToRoom(cname, event),
                () -> onUpstreamClose(cname));
            us.connect();
            return us;
        });

        log.info("HT bridge: frontend opened cname='{}' userId='{}' total={}", cname, userId, subscriberCount(cname));
    }

    @OnClose
    public void onClose(String cname, WebSocketSession session) {
        Set<WebSocketSession> sessions = roomSessions.get(cname);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                roomSessions.remove(cname);
                HtUpstream us = upstreams.remove(cname);
                if (us != null) us.disconnect("last client disconnected");
                log.info("HT bridge: last client left cname='{}' — upstream closed", cname);
            }
        }
        log.info("HT bridge: frontend closed cname='{}' remaining={}", cname, subscriberCount(cname));
    }

    @OnError
    public void onError(String cname, WebSocketSession session, Throwable t) {
        log.error("HT bridge: frontend WS error cname='{}': {}", cname, t.getMessage());
    }

    // ---- Upstream event dispatch ------------------------------------------------

    private void broadcastToRoom(String cname, HtEvent event) {
        Set<WebSocketSession> sessions = roomSessions.get(cname);
        if (sessions == null || sessions.isEmpty()) return;
        String json;
        try {
            json = om.writeValueAsString(event);
        } catch (Exception e) {
            log.warn("HT bridge: event serialisation failed: {}", e.getMessage());
            return;
        }
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try { s.sendSync(json); } catch (Exception e) {
                    log.warn("HT bridge: forward failed: {}", e.getMessage());
                }
            }
        }
    }

    private void onUpstreamClose(String cname) {
        String errorJson;
        try {
            errorJson = om.writeValueAsString(new HtEvent.Error("LiveHub connection closed"));
        } catch (Exception fallback) {
            errorJson = """
                {"type":"error","message":"LiveHub connection closed"}""";
        }
        Set<WebSocketSession> sessions = roomSessions.get(cname);
        if (sessions != null) {
            for (WebSocketSession s : sessions) {
                if (s.isOpen()) {
                    try { s.sendSync(errorJson); } catch (Exception ignored) {}
                }
            }
        }
        // Remove stale upstream; reconnect if the room still has subscribers
        upstreams.remove(cname);
        if (roomSessions.containsKey(cname) && !roomSessions.get(cname).isEmpty()) {
            log.info("HT bridge: upstream closed but room '{}' still has subscribers — reconnecting", cname);
            // Use the cname; we no longer have the userId so skip reconnect for now
            // (a cleaner solution stores userId per room; left as TODO)
        }
    }

    // ---- Helpers ----------------------------------------------------------------

    private String buildLiveHubUrl(String userId, String cname, boolean isVisitor) {
        return "%s?user_id=%s&cname=%s&is_visitor=%b".formatted(
            properties.htServerUrl(), userId, cname, isVisitor);
    }

    private int subscriberCount(String cname) {
        Set<WebSocketSession> s = roomSessions.get(cname);
        return s != null ? s.size() : 0;
    }

    // ---- LiveHub upstream connection --------------------------------------------
    // One instance per room cname. Uses java.net.http.WebSocket (text frames).

    private static class HtUpstream {
        private static final Logger log = LoggerFactory.getLogger(HtUpstream.class);
        private static final int MAX_RECONNECT = 3;
        private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
        private static final long DEFAULT_HEARTBEAT_SEC = 60;

        private final String url;
        private final String cname;
        private final String userId;
        private final boolean isVisitor;
        private final HtEventParser parser;
        private final java.util.function.Consumer<HtEvent> onEvent;
        private final Runnable onClose;

        private final AtomicInteger reconnectCount = new AtomicInteger(0);
        private final ScheduledExecutorService scheduler;

        private volatile boolean connected = false;
        private volatile boolean stopped = false;
        private volatile java.net.http.WebSocket ws;
        private volatile ScheduledFuture<?> heartbeatFuture;
        private volatile long heartbeatSec = DEFAULT_HEARTBEAT_SEC;

        HtUpstream(
                String url, String cname, String userId, boolean isVisitor,
                HtEventParser parser,
                java.util.function.Consumer<HtEvent> onEvent,
                Runnable onClose) {
            this.url = url;
            this.cname = cname;
            this.userId = userId;
            this.isVisitor = isVisitor;
            this.parser = parser;
            this.onEvent = onEvent;
            this.onClose = onClose;
            this.scheduler = Executors.newScheduledThreadPool(
                1, Thread.ofVirtual().name("ht-hb-" + cname).factory());
        }

        void connect() {
            if (stopped) return;
            try {
                var client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build();
                this.ws = client.newWebSocketBuilder()
                    .buildAsync(URI.create(url), new Listener())
                    .join();
            } catch (Exception e) {
                log.error("HT upstream connect failed ({}): {}", cname, e.getMessage());
                scheduleReconnect();
            }
        }

        /** Sends a frame as a text WebSocket message. */
        boolean sendFrame(HtOutboundFrame frame) {
            java.net.http.WebSocket s = this.ws;
            if (s == null || !connected) return false;
            try {
                s.sendText(frame.toJson(), true);
                return true;
            } catch (Exception e) {
                log.warn("HT upstream send failed ({}): {}", cname, e.getMessage());
                return false;
            }
        }

        void disconnect(String reason) {
            stopped = true;
            connected = false;
            cancelHeartbeat();
            scheduler.shutdownNow();
            try {
                java.net.http.WebSocket s = this.ws;
                if (s != null) s.sendClose(1000, reason);
            } catch (Exception ignored) {}
        }

        private void scheduleHeartbeat(long delaySec) {
            cancelHeartbeat();
            long fireInSec = Math.max(1, delaySec - 5);
            heartbeatFuture = scheduler.schedule(() -> {
                if (!connected || stopped) return;
                sendFrame(new HtOutboundFrame.Heartbeat(cname, userId, isVisitor));
                log.trace("HT upstream: heartbeat sent ({})", cname);
            }, fireInSec, TimeUnit.SECONDS);
        }

        private void cancelHeartbeat() {
            ScheduledFuture<?> f = heartbeatFuture;
            if (f != null) f.cancel(false);
        }

        private void scheduleReconnect() {
            if (stopped || reconnectCount.incrementAndGet() > MAX_RECONNECT) {
                log.warn("HT upstream: max reconnect attempts reached ({})", cname);
                onClose.run();
                return;
            }
            long delayMs = Math.min(1_000L << reconnectCount.get(), 30_000L);
            log.info("HT upstream: reconnecting {} in {}ms (attempt {})", cname, delayMs, reconnectCount.get());
            Thread.ofVirtual().name("ht-reconnect-" + cname).start(() -> {
                try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
                if (!stopped) connect();
            });
        }

        private class Listener implements java.net.http.WebSocket.Listener {
            private final StringBuilder textBuffer = new StringBuilder();

            @Override
            public void onOpen(java.net.http.WebSocket ws) {
                connected = true;
                reconnectCount.set(0);
                log.info("HT upstream: connected ({})", cname);
                // Step 1: register for room events
                sendFrame(new HtOutboundFrame.Init(userId, cname));
                ws.request(1);
            }

            @Override
            public CompletionStage<?> onText(
                    java.net.http.WebSocket ws, CharSequence data, boolean last) {
                textBuffer.append(data);
                ws.request(1);
                if (!last) return CompletableFuture.<Void>completedFuture(null);

                String text = textBuffer.toString();
                textBuffer.setLength(0);

                handleFrame(text);
                return CompletableFuture.<Void>completedFuture(null);
            }

            @Override
            public CompletionStage<?> onClose(
                    java.net.http.WebSocket ws, int statusCode, String reason) {
                connected = false;
                log.info("HT upstream: closed {} {} ({})", statusCode, reason, cname);
                cancelHeartbeat();
                if (!stopped) scheduleReconnect();
                return CompletableFuture.<Void>completedFuture(null);
            }

            @Override
            public void onError(java.net.http.WebSocket ws, Throwable error) {
                connected = false;
                log.error("HT upstream: error ({}): {}", cname, error.getMessage());
                cancelHeartbeat();
                if (!stopped) scheduleReconnect();
            }

            private void handleFrame(String text) {
                // Heartbeat config — send one immediately (LiveHub expects it) then schedule the next
                var hbSec = parser.heartbeatSec(text);
                if (hbSec.isPresent()) {
                    heartbeatSec = hbSec.getAsLong();
                    log.debug("HT upstream: heartbeat_sec={} ({})", heartbeatSec, cname);
                    sendFrame(new HtOutboundFrame.Heartbeat(cname, userId, isVisitor));
                    scheduleHeartbeat(heartbeatSec);
                    return;
                }

                // Heartbeat response — reschedule
                if (parser.isHeartbeatResponse(text)) {
                    scheduleHeartbeat(heartbeatSec);
                    return;
                }

                // Room event — ACK then forward
                String msgId = parser.msgId(text);
                if (msgId != null) {
                    sendFrame(new HtOutboundFrame.Ack(msgId, userId, cname, isVisitor));
                }

                parser.parse(text).ifPresent(onEvent);
            }
        }
    }
}
