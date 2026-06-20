package com.jilali.realtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliProperties;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * One LiveHub upstream WebSocket connection per room cname.
 *
 * <p>Uses {@code java.net.http.WebSocket} — plain TCP, no HTTP/2 or Netty pipeline —
 * identical to what a browser tab does.  The Micronaut/Netty pipeline was being killed by
 * the CDN; the JDK built-in client matches the original {@code fireRoomWebSocket} behaviour.
 *
 * <p>Lifecycle is owned by {@link RoomRealtimeRegistry}: {@link #connect(String, String, boolean)}
 * initiates the async connection and returns a {@link CompletableFuture} that callers use to
 * attach error handling.  All callbacks are set up by the caller so that stale callbacks from
 * previous connection attempts for the same cname can never affect the current connector.
 *
 * <p>LiveHub plain-JSON protocol:
 * <ol>
 *   <li>On open → send {@code {"user_id":169335562,"cname":"…","action":1}} —
 *       {@code user_id} is a bare JSON number, matching {@code fireRoomWebSocket}'s
 *       {@code JSON.stringify({user_id: myid, ...})} where {@code myid} comes from
 *       {@code JSON.parse(jwt).uid} (a number, never a quoted string)</li>
 *   <li>Server replies {@code {"heartbeat_sec":60}} → send first heartbeat + schedule next 5 s early</li>
 *   <li>Every event carries {@code msg_id} → ACK with {@code {"msg_id":"…","action":3,…}}</li>
 *   <li>Server confirms heartbeat with {@code {"heartbeat_time":…}} → reschedule next</li>
 * </ol>
 */
public class HtLiveHubUpstreamConnector implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HtLiveHubUpstreamConnector.class);

    /**
     * Single shared {@link HttpClient}, reused across every upstream connection.
     *
     * <p>The JDK's {@code HttpClient} must stay reachable via a strong reference for as long as
     * any operation depends on it — including an open WebSocket built from it. A client with no
     * strong reference is eligible for GC at any time; once collected, any WebSocket it built is
     * silently torn down with close code 1006. Building a fresh {@code HttpClient} per {@code
     * connect()} call (the previous approach here) made every connection a few seconds from a GC
     * pass away from this exact failure. {@code HttpClient} is documented as immutable and safe
     * for concurrent reuse, so one shared instance for the process lifetime is the fix.
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final HtNotifyMapper mapper;
    private final ObjectMapper om;
    private final JilaliProperties properties;

    // ── caller-supplied delegates (stable across the lifetime of one instance) ──
    private volatile Consumer<RoomRealtimeEvent> eventListener;
    private volatile Runnable disconnectListener;

    // ── connection state (reset on each call to connect()) ──
    private volatile WebSocket ws;
    private volatile String cname;
    private volatile String userId;
    private volatile boolean isVisitor;
    private volatile boolean connected;
    private volatile long heartbeatIntervalSec = 60;
    private volatile Thread heartbeatThread;
    private volatile Thread pingThread;
    private static final Duration PING_INTERVAL = Duration.ofSeconds(2);
    /**
     * Tail of the send chain — {@code WebSocket.sendText} permits only one outstanding send at
     * a time; a second call before the first completes fails (asynchronously, via the discarded
     * future) with {@code IllegalStateException}. Every {@link #send} call composes onto this
     * tail instead of calling {@code sendText} directly, so sends from different threads (the
     * connect callback, the heartbeat timer, the inbound-ACK callback) are strictly serialized.
     */
    private volatile CompletableFuture<WebSocket> sendChain = CompletableFuture.completedFuture(null);

    public HtLiveHubUpstreamConnector(HtNotifyMapper mapper, ObjectMapper om, JilaliProperties properties) {
        this.mapper = mapper;
        this.om = om;
        this.properties = properties;
    }

    /**
     * Registers callbacks that survive for the lifetime of this connector instance.
     * Safe to call multiple times (e.g. on reconnect).
     */
    public void attach(Consumer<RoomRealtimeEvent> eventListener, Runnable disconnectListener) {
        this.eventListener = eventListener;
        this.disconnectListener = disconnectListener;
    }

    /**
     * Initiates a WebSocket connection to LiveHub.
     *
     * @return a {@link CompletableFuture} that completes when the WebSocket is open.
     *         The caller attaches error handling via {@code handle()} or {@code whenComplete()}
     *         so that this connector's callbacks remain scoped to this call.
     */
    public CompletableFuture<Void> connect(String userId, String cname, boolean isVisitor) {
        this.userId = userId;
        this.cname = cname;
        this.isVisitor = isVisitor;

        String url = "wss://uploadprocn.hellotalk8.com/livehub/ws/conn"
            + "?user_id=" + userId
            + "&cname=" + cname
            + "&is_visitor=" + isVisitor;

        if (log.isDebugEnabled()) {
            log.debug("LiveHub WS connecting to: {}", url);
        }

        // The handshake otherwise carries no device-context headers, unlike every other call
        // this codebase makes to the *.hellotalk8.com family (see DefaultHeadersClientFilter) —
        // it looks like a bare Java client rather than the app, which is consistent with the
        // upstream silently severing the socket (close code 1006) a few seconds after the
        // handshake succeeds, well before the 60s app-level heartbeat is ever due.
        return HTTP_CLIENT.newWebSocketBuilder()
            .header("User-Agent", "ios;6.1.0;iPhone;18.5;")
            .header("x-ht-version", "6.1.0")
            .header("x-ht-os", "ios")
            .header("x-ht-channel", "AppStore")
            .header("x-ht-lang", "English")
            .header("x-ht-ui-mode", "1")
            .header("x-ht-timezone", "1.00")
            .header("x-ht-tzid", "Africa/Casablanca")
            .header("x-ht-device", "iPhone")
            .header("x-ht-os-version", "18.5")
            .header("x-ht-build", "135")
            .header("x-ht-did", properties.deviceId())
            .header("x-ht-uid", userId)
            .header("Accept-Language", "en-MA;q=1.0, fr-MA;q=0.9, ar-MA;q=0.8")
            .header("Authorization", "Bearer " + properties.defaultAuthToken())
            .header("x-ht-token", "Bearer " + properties.defaultAuthToken())
            .buildAsync(URI.create(url), new Listener())
            .thenAccept(ws -> {
                this.ws = ws;
                connected = true;
                log.info("LiveHub WS connected cname={}", cname);
                send(new InitFrame(userIdAsLong(), cname, 1));
                // Production no longer replies with {"heartbeat_sec":...} to acknowledge the
                // init frame (confirmed via wire trace: room broadcast events arrive over the
                // socket — subscription is keyed off the cname/user_id query params, not this
                // frame — but no heartbeat_sec or heartbeat_time frame ever does). Heartbeat
                // proactively instead of waiting on a reply that never comes.
                sendHeartbeat();
                scheduleHeartbeat();
                // The app-level heartbeat above did not keep the connection alive either, even
                // after confirming via wire trace that it really does reach the server — every
                // connection still died at the same ~3.5s mark. That points at a layer below the
                // app protocol: many reverse proxies/load balancers track liveness via the raw
                // RFC 6455 WS ping/pong control frames (inspectable without parsing app JSON),
                // not the app's own heartbeat message. Ping at a fixed short interval to test
                // that theory; this is independent of, and additional to, the app heartbeat.
                schedulePing();
                ws.request(1);
            });
        // Errors are NOT handled here — caller handles them via the returned CF.
        // This prevents stale callbacks from old connectors affecting the current one.
    }

    @Override
    public void close() {
        connected = false;
        cancelHeartbeat();
        cancelPing();
        WebSocket s = this.ws;
        if (s != null) {
            try {
                s.sendClose(1000, "normal");
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    // ── Inbound frame handling ────────────────────────────────────────────────

    private void handleFrame(String text) {
        if (log.isTraceEnabled()) {
            log.trace("LiveHub RX cname={}: {}", cname, text);
        }

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
        mapper.map(text).ifPresent(event -> {
            Consumer<RoomRealtimeEvent> l = eventListener;
            if (l != null) l.accept(event);
        });
    }

    private void sendHeartbeat() {
        send(new HeartbeatFrame(cname, userIdAsLong(), 2, isVisitor));
    }

    private void sendAck(String msgId) {
        send(new AckFrame(msgId, 3, userIdAsLong(), cname, isVisitor));
    }

    /**
     * The reference browser client ({@code fireRoomWebSocket} in scriptv2.js) derives
     * {@code user_id} from {@code JSON.parse(jwt payload).uid} — a bare JSON number — and
     * sends it as a number in every frame (init/heartbeat/ack). Our frames declared it as
     * a {@code String}, so Jackson sent {@code "user_id":"169335562"} (quoted) instead of
     * {@code "user_id":169335562}. Testing whether that type mismatch is why the server
     * never sends back {@code heartbeat_sec} and kills the connection ~3.5s in.
     */
    private long userIdAsLong() {
        return Long.parseLong(userId);
    }

    private synchronized void send(Object frame) {
        WebSocket s = this.ws;
        if (s == null || !connected) return;
        String json;
        try {
            json = om.writeValueAsString(frame);
        } catch (Exception e) {
            log.warn("LiveHub WS send serialization failed cname={}: {}", cname, e.getMessage());
            return;
        }
        // Chain onto the previous send regardless of how it finished — a failed predecessor
        // must not stall every send after it.
        sendChain = sendChain
            .handle((ignoredResult, ignoredEx) -> null)
            .thenCompose(ignored -> s.sendText(json, true))
            .exceptionally(e -> {
                log.warn("LiveHub WS send failed cname={}: {}", cname, e.getMessage());
                return null;
            });
    }

    /**
     * Sends a zero-length RFC 6455 WS-protocol ping — distinct from the app-level JSON
     * heartbeat, and routed through the same {@link #sendChain} since {@code sendPing} is
     * subject to the identical "one outstanding send" constraint as {@code sendText}.
     */
    private synchronized void sendPing() {
        WebSocket s = this.ws;
        if (s == null || !connected) return;
        sendChain = sendChain
            .handle((ignoredResult, ignoredEx) -> null)
            .thenCompose(ignored -> s.sendPing(ByteBuffer.allocate(0)))
            .exceptionally(e -> {
                log.warn("LiveHub WS ping failed cname={}: {}", cname, e.getMessage());
                return null;
            });
    }

    private void schedulePing() {
        pingThread = Thread.ofVirtual()
            .name("livehub-ping-" + cname)
            .start(() -> {
                while (connected) {
                    try {
                        Thread.sleep(PING_INTERVAL);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (connected) sendPing();
                }
            });
    }

    private void cancelPing() {
        Thread t = pingThread;
        pingThread = null;
        if (t != null) {
            t.interrupt();
        }
    }

    private void scheduleHeartbeat() {
        long delaySec = Math.max(1, heartbeatIntervalSec - 5);
        heartbeatThread = Thread.ofVirtual()
            .name("livehub-hb-" + cname)
            .start(() -> {
                try {
                    Thread.sleep(Duration.ofSeconds(delaySec));
                    if (connected) sendHeartbeat();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
    }

    private void cancelHeartbeat() {
        Thread ht = heartbeatThread;
        if (ht != null) {
            ht.interrupt();
            heartbeatThread = null;
        }
    }

    // ── Frame records ───────────────────────────────────────────────────────

    private record InitFrame(
        @JsonProperty("user_id") long userId,
        String cname,
        int action
    ) {}

    private record HeartbeatFrame(
        String cname,
        @JsonProperty("user_id") long userId,
        int action,
        @JsonProperty("is_visitor") boolean isVisitor
    ) {}

    private record AckFrame(
        @JsonProperty("msg_id") String msgId,
        int action,
        @JsonProperty("user_id") long userId,
        String cname,
        @JsonProperty("is_visitor") boolean isVisitor
    ) {}

    // ── WebSocket listener ──────────────────────────────────────────────────

    private class Listener implements WebSocket.Listener {

        private final StringBuilder textBuffer = new StringBuilder(512);

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String frame = textBuffer.toString();
                textBuffer.setLength(0);
                handleFrame(frame);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connected = false;
            cancelHeartbeat();
            cancelPing();
            log.info("LiveHub WS closed cname={} status={} reason={}", cname, statusCode, reason);
            Runnable listener = disconnectListener;
            if (listener != null) listener.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("LiveHub WS error cname={}: {}", cname, error.getMessage());
            Consumer<RoomRealtimeEvent> l = eventListener;
            if (l != null) {
                l.accept(new RoomRealtimeEvent.Error("LiveHub upstream error: " + error.getMessage()));
            }
        }
    }
}
