package com.jilali.realtime;

import com.jilali.realtime.dto.RoomRealtimeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** One LiveHub upstream WebSocket connection per room. */
public class HtLiveHubUpstreamConnector implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HtLiveHubUpstreamConnector.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String LIVEHUB_WS_URL = "wss://uploadprocn.hellotalk8.com/livehub/ws/conn";

    private volatile Consumer<RoomRealtimeEvent> eventListener;
    private volatile Runnable disconnectListener;

    private volatile WebSocket ws;
    private volatile Session session;
    private volatile ScheduledFuture<?> heartbeatFuture;
    private volatile ScheduledExecutorService heartbeatScheduler;
    private volatile CompletableFuture<WebSocket> sendChain = CompletableFuture.completedFuture(null);

    private final HtNotifyMapper mapper;

    public HtLiveHubUpstreamConnector(HtNotifyMapper mapper) {
        this.mapper = mapper;
    }

    public void attach(Consumer<RoomRealtimeEvent> eventListener, Runnable disconnectListener) {
        this.eventListener = eventListener;
        this.disconnectListener = disconnectListener;
    }

    public CompletableFuture<Void> connect(String userId, String cname, boolean isVisitor) {
        this.session = new Session(Long.parseLong(userId), cname, isVisitor, 60, false);
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("livehub-hb-" + cname).factory());

        log.debug("LiveHub WS connecting to: {}?user_id={}&cname={}&is_visitor={}",
            LIVEHUB_WS_URL, userId, cname, isVisitor);

        return HTTP_CLIENT.newWebSocketBuilder()
            .buildAsync(URI.create(LIVEHUB_WS_URL
                + "?user_id=" + userId + "&cname=" + cname + "&is_visitor=" + isVisitor),
                new Listener())
            .thenAccept(ws -> {
                this.ws = ws;
                session = new Session(session.userId, session.cname, session.isVisitor, session.heartbeatIntervalSec, true);
                log.info("LiveHub WS connected cname={}", session.cname);
                send(initFrame());
                ws.request(1);
            });
    }

    @Override
    public void close() {
        Session s = session;
        if (s == null) return;
        session = new Session(s.userId, s.cname, s.isVisitor, s.heartbeatIntervalSec, false);
        cancelHeartbeat();
        sendChain = CompletableFuture.completedFuture(null);
        if (ws != null) {
            try {
                ws.sendClose(1000, "normal");
            } catch (Exception _) {}
        }
    }

    private String initFrame() {
        return "{\"user_id\":" + session.userId + ",\"cname\":\"" + session.cname + "\",\"action\":1}";
    }

    private String heartbeatFrame() {
        return "{\"cname\":\"" + session.cname + "\",\"user_id\":" + session.userId
            + ",\"action\":2,\"is_visitor\":" + session.isVisitor + "}";
    }

    private String ackFrame(String msgId) {
        return "{\"msg_id\":\"" + msgId + "\",\"action\":3,\"user_id\":" + session.userId
            + ",\"cname\":\"" + session.cname + "\",\"is_visitor\":" + session.isVisitor + "}";
    }

    private void handleFrame(String text) {
        log.trace("LiveHub RX cname={}: {}", session.cname, text);

        mapper.heartbeatSec(text)
            .ifPresentOrElse(hbSec -> {
                    session = new Session(session.userId, session.cname, session.isVisitor, hbSec, session.connected);
                    sendHeartbeat();
                    scheduleHeartbeat();
                },
                () -> {
                    if (mapper.isHeartbeatResponse(text)) {
                        scheduleHeartbeat();
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
        send(heartbeatFrame());
    }

    private void sendAck(String msgId) {
        send(ackFrame(msgId));
    }

    private void send(String json) {
        WebSocket s = this.ws;
        if (s == null || !session.connected) return;
        sendChain = sendChain
            .handle((_, _) -> null)
            .thenCompose(_ -> s.sendText(json, true))
            .exceptionally(e -> {
                log.warn("LiveHub WS send failed cname={}: {}", session.cname, e.getMessage());
                return null;
            });
    }

    private void scheduleHeartbeat() {
        if (heartbeatFuture != null) { heartbeatFuture.cancel(false); heartbeatFuture = null; }
        long delaySec = Math.max(1, session.heartbeatIntervalSec - 5);
        heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(
            () -> { if (session.connected) sendHeartbeat(); },
            delaySec, session.heartbeatIntervalSec, TimeUnit.SECONDS);
    }

    private void cancelHeartbeat() {
        ScheduledFuture<?> f = heartbeatFuture;
        if (f != null) { f.cancel(false); heartbeatFuture = null; }
        ScheduledExecutorService s = heartbeatScheduler;
        if (s != null) { s.shutdownNow(); heartbeatScheduler = null; }
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
            Session s = session;
            session = new Session(s.userId, s.cname, s.isVisitor, s.heartbeatIntervalSec, false);
            cancelHeartbeat();
            log.info("LiveHub WS closed cname={} status={} reason={}", session.cname, statusCode, reason);
            Runnable listener = disconnectListener;
            if (listener != null) listener.run();
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
