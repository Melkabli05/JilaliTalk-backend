// src/main/java/com/jilali/realtime/HtLiveHubUpstreamConnector.java
package com.jilali.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jilali.core.ws.ExponentialBackoff;
import com.jilali.core.ws.HeartbeatPump;
import com.jilali.core.ws.SequentialSender;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * One LiveHub upstream WebSocket connection per room. An unexpected close (network drop, not
 * our own {@link #close()}) triggers an internal reconnect loop with capped exponential
 * backoff — {@link #connect} only reflects the first attempt in its returned future, so
 * {@code RoomEventSource}'s existing failure handling for "upstream unreachable from the
 * start" is unaffected. This reconnect loop is the only one in play here — {@code
 * RoomEventSource} does not itself retry, so a future change adding retry there too would
 * stack two independent backoff loops.
 */
public class HtLiveHubUpstreamConnector implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HtLiveHubUpstreamConnector.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String LIVEHUB_WS_URL = "wss://uploadprocn.hellotalk8.com/livehub/ws/conn";

    private final HtNotifyMapper mapper;
    private final ObjectMapper om;
    private final SequentialSender sender = new SequentialSender();
    private final HeartbeatPump heartbeat = new HeartbeatPump("livehub-hb");
    private final ExponentialBackoff backoff = new ExponentialBackoff(Duration.ofSeconds(1), Duration.ofSeconds(30));

    private volatile Consumer<RoomRealtimeEvent> eventListener;
    private volatile Runnable disconnectListener;

    private volatile WebSocket ws;
    private volatile Session session;
    private volatile boolean intentionalClose;

    public HtLiveHubUpstreamConnector(HtNotifyMapper mapper, ObjectMapper om) {
        this.mapper = mapper;
        this.om = om;
    }

    public void attach(Consumer<RoomRealtimeEvent> eventListener, Runnable disconnectListener) {
        this.eventListener = eventListener;
        this.disconnectListener = disconnectListener;
    }

    public CompletableFuture<Void> connect(String userId, String cname, boolean isVisitor) {
        this.session = new Session(Long.parseLong(userId), cname, isVisitor, 60, false);
        this.intentionalClose = false;
        return attemptConnect();
    }

    private CompletableFuture<Void> attemptConnect() {
        Session s = session;
        log.debug("LiveHub WS connecting to: {}?user_id={}&cname={}&is_visitor={}",
            LIVEHUB_WS_URL, s.userId, s.cname, s.isVisitor);

        return HTTP_CLIENT.newWebSocketBuilder()
            .buildAsync(URI.create(LIVEHUB_WS_URL
                + "?user_id=" + s.userId + "&cname=" + s.cname + "&is_visitor=" + s.isVisitor),
                new Listener())
            .thenAccept(sock -> {
                this.ws = sock;
                session = withConnected(session, true);
                backoff.reset();
                log.info("LiveHub WS connected cname={}", session.cname);
                send(initFrame());
                sock.request(1);
            });
    }

    private void reconnectInBackground() {
        if (intentionalClose) return;
        Duration delay = backoff.nextDelay();
        log.info("LiveHub WS reconnecting cname={} in {}ms", session.cname, delay.toMillis());
        CompletableFuture.runAsync(() -> {
            if (intentionalClose) return;
            attemptConnect().exceptionally(ex -> {
                log.warn("LiveHub WS reconnect attempt failed cname={}: {}", session.cname, ex.getMessage());
                reconnectInBackground();
                return null;
            });
        }, CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS));
    }

    @Override
    public void close() {
        intentionalClose = true;
        Session s = session;
        if (s == null) return;
        session = withConnected(s, false);
        heartbeat.stop();
        sender.reset();
        if (ws != null) {
            try { ws.sendClose(1000, "normal"); } catch (Exception ignored) {}
        }
    }

    private String initFrame() {
        return writeJson(om.createObjectNode()
            .put("user_id", session.userId)
            .put("cname", session.cname)
            .put("action", 1));
    }

    private String heartbeatFrame() {
        return writeJson(om.createObjectNode()
            .put("cname", session.cname)
            .put("user_id", session.userId)
            .put("action", 2)
            .put("is_visitor", session.isVisitor));
    }

    private String ackFrame(String msgId) {
        return writeJson(om.createObjectNode()
            .put("msg_id", msgId)
            .put("action", 3)
            .put("user_id", session.userId)
            .put("cname", session.cname)
            .put("is_visitor", session.isVisitor));
    }

    private String writeJson(ObjectNode node) {
        try {
            return om.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build LiveHub frame", e);
        }
    }

    private void handleFrame(String text) {
        log.trace("LiveHub RX cname={}: {}", session.cname, text);

        mapper.heartbeatSec(text)
            .ifPresentOrElse(hbSec -> {
                    session = withHeartbeatInterval(session, hbSec);
                    sendHeartbeat();
                    long delaySec = Math.max(1, hbSec - 5);
                    heartbeat.start(Duration.ofSeconds(delaySec), Duration.ofSeconds(hbSec), this::sendHeartbeat);
                },
                () -> {
                    if (mapper.isHeartbeatResponse(text)) {
                        // server ack of our heartbeat — the pump is already scheduled, nothing to do
                    } else {
                        mapper.msgId(text).ifPresent(this::sendAck);
                        mapper.map(text).ifPresent(event -> {
                            Consumer<RoomRealtimeEvent> l = eventListener;
                            if (l != null) l.accept(event);
                        });
                    }
                });
    }

    private void sendHeartbeat() {
        if (session.connected) send(heartbeatFrame());
    }

    private void sendAck(String msgId) {
        send(ackFrame(msgId));
    }

    private void send(String json) {
        WebSocket s = this.ws;
        if (s == null || !session.connected) return;
        sender.enqueue(() -> s.sendText(json, true),
            e -> log.warn("LiveHub WS send failed cname={}: {}", session.cname, e.getMessage()));
    }

    private static Session withConnected(Session s, boolean connected) {
        return new Session(s.userId, s.cname, s.isVisitor, s.heartbeatIntervalSec, connected);
    }

    private static Session withHeartbeatInterval(Session s, long hbSec) {
        return new Session(s.userId, s.cname, s.isVisitor, hbSec, s.connected);
    }

    private record Session(long userId, String cname, boolean isVisitor, long heartbeatIntervalSec, boolean connected) {}

    private class Listener implements WebSocket.Listener {

        private final StringBuilder textBuffer = new StringBuilder(512);

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String frame = textBuffer.toString();
                textBuffer.setLength(0);
                handleFrame(frame);
            }
            ws.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            session = withConnected(session, false);
            heartbeat.stop();
            log.info("LiveHub WS closed cname={} status={} reason={}", session.cname, statusCode, reason);
            if (intentionalClose) {
                Runnable listener = disconnectListener;
                if (listener != null) listener.run();
            } else {
                reconnectInBackground();
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.warn("LiveHub WS error cname={}: {}", session.cname, error.getMessage());
            Consumer<RoomRealtimeEvent> l = eventListener;
            if (l != null) {
                l.accept(new RoomRealtimeEvent.Error("LiveHub upstream error: " + error.getMessage()));
            }
        }
    }
}
