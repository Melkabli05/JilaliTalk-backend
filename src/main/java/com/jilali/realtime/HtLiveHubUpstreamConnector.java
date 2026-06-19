package com.jilali.realtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnError;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * One instance per active room {@code cname}, created by {@link RoomRealtimeRegistry} via
 * {@code WebSocketClient.connect(HtLiveHubUpstreamConnector.class, uri)}. Speaks LiveHub's
 * plain-JSON {@code action}-based protocol: init on open, heartbeat on the server's
 * schedule (5s before it expires), and ack for every event frame carrying {@code msg_id}
 * — LiveHub stops delivering events on a connection that skips acks.
 */
@ClientWebSocket
public abstract class HtLiveHubUpstreamConnector implements AutoCloseable {

    private final HtNotifyMapper mapper;
    private final ObjectMapper om;
    private final Queue<String> pending = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("livehub-heartbeat").factory());

    private volatile Consumer<RoomRealtimeEvent> eventListener;
    private volatile Runnable disconnectListener;
    private volatile WebSocketSession session;
    private volatile ScheduledFuture<?> heartbeatFuture;
    private volatile long heartbeatIntervalSec = 60;
    private volatile String userId;
    private volatile String cname;
    private volatile boolean isVisitor;

    protected HtLiveHubUpstreamConnector(HtNotifyMapper mapper, ObjectMapper om) {
        this.mapper = mapper;
        this.om = om;
    }

    /**
     * Called once by {@link RoomRealtimeRegistry} right after this instance connects.
     * Any frames that arrived between the WebSocket opening and this call being made are
     * buffered and flushed here, in order, before any subsequently-arriving frame.
     */
    public void attach(Consumer<RoomRealtimeEvent> eventListener, Runnable disconnectListener) {
        this.eventListener = eventListener;
        this.disconnectListener = disconnectListener;
        String buffered;
        while ((buffered = pending.poll()) != null) {
            handleFrame(buffered);
        }
    }

    @OnOpen
    public void onOpen(@QueryValue("user_id") String userId, @QueryValue("cname") String cname,
                        @QueryValue("is_visitor") boolean isVisitor, WebSocketSession session) {
        this.userId = userId;
        this.cname = cname;
        this.isVisitor = isVisitor;
        this.session = session;
        send(new InitFrame(userId, cname, 1));
    }

    @OnMessage
    public void onMessage(String text) {
        if (eventListener == null) {
            pending.add(text);
            return;
        }
        handleFrame(text);
    }

    @OnClose
    public void onClose() {
        cancelHeartbeat();
        if (disconnectListener != null) disconnectListener.run();
    }

    @OnError
    public void onError(Throwable error) {
        if (eventListener != null) {
            eventListener.accept(new RoomRealtimeEvent.Error("LiveHub upstream error: " + error.getMessage()));
        }
    }

    @Override
    public void close() {
        cancelHeartbeat();
        scheduler.shutdownNow();
    }

    private void handleFrame(String text) {
        var hbSec = mapper.heartbeatSec(text);
        if (hbSec.isPresent()) {
            heartbeatIntervalSec = hbSec.getAsLong();
            sendHeartbeat();
            scheduleHeartbeat();
            return;
        }
        if (mapper.isHeartbeatResponse(text)) {
            scheduleHeartbeat();
            return;
        }
        mapper.msgId(text).ifPresent(this::sendAck);
        mapper.map(text).ifPresent(event -> eventListener.accept(event));
    }

    private void sendHeartbeat() {
        send(new HeartbeatFrame(cname, userId, 2, isVisitor));
    }

    private void sendAck(String msgId) {
        send(new AckFrame(msgId, 3, userId, cname, isVisitor));
    }

    private void send(Object frame) {
        WebSocketSession s = this.session;
        if (s == null) return;
        try {
            s.sendAsync(om.writeValueAsString(frame));
        } catch (Exception e) {
            // These are tiny internal records with no nullable fields that could fail to
            // serialize; if this ever throws, dropping one outbound frame beats crashing
            // the connection over it.
        }
    }

    private void scheduleHeartbeat() {
        if (heartbeatFuture != null) heartbeatFuture.cancel(false);
        long fireInSec = Math.max(1, heartbeatIntervalSec - 5);
        heartbeatFuture = scheduler.schedule(this::sendHeartbeat, fireInSec, TimeUnit.SECONDS);
    }

    private void cancelHeartbeat() {
        if (heartbeatFuture != null) heartbeatFuture.cancel(false);
    }

    private record InitFrame(@JsonProperty("user_id") String userId, String cname, int action) {}

    private record HeartbeatFrame(
        String cname, @JsonProperty("user_id") String userId, int action,
        @JsonProperty("is_visitor") boolean isVisitor) {}

    private record AckFrame(
        @JsonProperty("msg_id") String msgId, int action, @JsonProperty("user_id") String userId,
        String cname, @JsonProperty("is_visitor") boolean isVisitor) {}
}