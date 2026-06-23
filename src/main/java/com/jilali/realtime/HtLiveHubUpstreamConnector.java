package com.jilali.realtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
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

    /**
     * Temporary capture of every raw LiveHub frame, verbatim, for auditing real wire shapes
     * per notify_type — remove once that's done. Deliberately every frame including
     * heartbeat/ack noise, not just mapped events, since the point is to see what's actually
     * on the wire rather than what {@link HtNotifyMapper} already assumes about it.
     */
    private static final Path CAPTURE_FILE = Path.of("/tmp/livehub-frames.jsonl");

    private final HtNotifyMapper mapper;
    private final ObjectMapper om;

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
    /**
     * Tail of the send chain — {@code WebSocket.sendText} permits only one outstanding send at
     * a time; a second call before the first completes fails (asynchronously, via the discarded
     * future) with {@code IllegalStateException}. Every {@link #send} call composes onto this
     * tail instead of calling {@code sendText} directly, so sends from different threads (the
     * connect callback, the heartbeat timer, the inbound-ACK callback) are strictly serialized.
     */
    private volatile CompletableFuture<WebSocket> sendChain = CompletableFuture.completedFuture(null);

    public HtLiveHubUpstreamConnector(HtNotifyMapper mapper, ObjectMapper om) {
        this.mapper = mapper;
        this.om = om;
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

        return HTTP_CLIENT.newWebSocketBuilder()
            .buildAsync(URI.create(url), new Listener())
            .thenAccept(ws -> {
                this.ws = ws;
                connected = true;
                log.info("LiveHub WS connected cname={}", cname);
                send(new InitFrame(userIdAsLong(), cname, 1));
                ws.request(1);
            });
        // Errors are NOT handled here — caller handles them via the returned CF.
        // This prevents stale callbacks from old connectors affecting the current one.
    }

    @Override
    public void close() {
        connected = false;
        cancelHeartbeat();
        // Reset sendChain so any in-flight sends from the old connection cannot chain into
        // a new connection's WebSocket after close() + connect() is called again.
        sendChain = CompletableFuture.completedFuture(null);
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
        captureRawFrame(text);

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

    private void captureRawFrame(String text) {
        try {
            String line = Instant.now() + " | cname=" + cname + " | " + text + System.lineSeparator();
            Files.writeString(CAPTURE_FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("Failed to capture LiveHub frame to {}: {}", CAPTURE_FILE, e.getMessage());
        }
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
     * sends it as a number in every frame (init/heartbeat/ack). Sending it quoted instead
     * (a {@code String} field) made the server silently reject the init frame: it never
     * replied with {@code heartbeat_sec}, and the connection died ~3.5s in every time.
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
