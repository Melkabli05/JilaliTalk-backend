package com.jilali.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.crypto.ApkSignatureGenerator;
import com.jilali.crypto.QqTeaCipher;
import com.jilali.im.dto.ImEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.zip.Inflater;

/**
 * One {@code ht_im/sock} connection per logged-in JilaliTalk user — account-level (DMs,
 * profile visits, room shares, session/ban status), as opposed to
 * {@link com.jilali.realtime.HtLiveHubUpstreamConnector}'s per-room LiveHub connection.
 *
 * <p>Binary protocol ported from the reference client (connectwebsock.js):
 * <ol>
 *   <li>Connect to {@code wss://api-global.hellotalk8.com/ht_im/sock?userid=<uid>}</li>
 *   <li>Send a login packet ({@link ImPacket#CMD_LOGIN}) carrying the JWT, device info, and
 *       an APK-signature HMAC ({@link ApkSignatureGenerator}) the server expects from a real
 *       device — server replies with {@code data.session_key}, the QQTEA key for everything
 *       after.</li>
 *   <li>Heartbeat every 30s ({@link ImPacket#CMD_HEARTBEAT}).</li>
 *   <li>Push notifications arrive as {@code 0xF2} packets: ACK immediately, then
 *       QQTEA-decrypt + optionally zlib-inflate the payload to get the notification JSON.</li>
 * </ol>
 *
 * <p>Deliberately out of scope for now: typing indicators (0xF5), offline-message sync, and
 * group-message sync (cmdIds 16454/29967/29968/16386) — these are DM-chat-history features,
 * not needed for account-level notifications (profile visits, room shares, ban status).
 */
public class HtImSocketConnector implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HtImSocketConnector.class);

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;

    private final HtImMessageMapper mapper;
    private final ObjectMapper om;
    /** The reference client calls an undefined {@code nextSeq()} (a bug — never declared
     *  anywhere in connectwebsock.js). Sequence numbers only need to be locally unique per
     *  connection for ACK correlation, so a simple wrapping per-connector counter serves the
     *  same purpose. */
    private final AtomicInteger sequence = new AtomicInteger(1);

    private volatile Consumer<ImEvent> eventListener;
    private volatile Runnable disconnectListener;

    private volatile WebSocket ws;
    private volatile long userId;
    private volatile boolean connected;
    private volatile byte[] sessionKey;
    private volatile Thread heartbeatThread;
    private volatile CompletableFuture<WebSocket> sendChain = CompletableFuture.completedFuture(null);

    public HtImSocketConnector(HtImMessageMapper mapper, ObjectMapper om) {
        this.mapper = mapper;
        this.om = om;
    }

    public void attach(Consumer<ImEvent> eventListener, Runnable disconnectListener) {
        this.eventListener = eventListener;
        this.disconnectListener = disconnectListener;
    }

    /**
     * @param jwt the real HelloTalk JWT for this user (from the token pool — see
     *            {@code com.jilali.auth.HelloTalkTokenPoolRepository})
     */
    public CompletableFuture<Void> connect(long uid, String jwt, String deviceId) {
        this.userId = uid;
        String wsUrl = "wss://api-global.hellotalk8.com/ht_im/sock?userid=" + uid;

        return HTTP_CLIENT.newWebSocketBuilder()
            .buildAsync(URI.create(wsUrl), new Listener())
            .thenAccept(socket -> {
                this.ws = socket;
                this.connected = true;
                log.info("ht_im/sock connected uid={}", uid);
                sendLogin(jwt, deviceId);
                scheduleHeartbeat();
                socket.request(1);
            });
    }

    @Override
    public void close() {
        connected = false;
        cancelHeartbeat();
        WebSocket s = this.ws;
        if (s != null) {
            try {
                s.sendClose(1000, "normal");
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    // ── Outbound ────────────────────────────────────────────────────────────

    private void sendLogin(String jwt, String deviceId) {
        String apkSig = ApkSignatureGenerator.generate(deviceId, System.currentTimeMillis());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jwt", jwt);
        payload.put("mobile_operator", "Orange");
        payload.put("operator_country", "ma");
        payload.put("android_apk_signature", apkSig);
        payload.put("app_version", "6.3.40(11126,google)");
        payload.put("background_reconnect", 0);
        payload.put("channel", "com.hellotalk.core.app.NihaotalkApplication");
        payload.put("client_lang", "English");
        payload.put("current_version", 394024);
        payload.put("device_detail", deviceId);
        payload.put("device_id", deviceId);
        payload.put("is_version_update", 0);
        payload.put("net_type", 1);
        payload.put("os_lang", "en");
        payload.put("os_version", "11");
        payload.put("terminal_type", 1);

        try {
            byte[] body = om.writeValueAsBytes(payload);
            send(ImPacket.build(userId, ImPacket.CMD_LOGIN, sequence.getAndIncrement() & 0xFFFF, body));
        } catch (Exception e) {
            log.warn("ht_im/sock login send failed: {}", e.getMessage());
        }
    }

    private void sendHeartbeat() {
        ByteBuffer body = ByteBuffer.allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        body.putInt(0, (int) userId);
        body.putLong(4, System.currentTimeMillis());
        send(ImPacket.build(userId, ImPacket.CMD_HEARTBEAT, sequence.getAndIncrement() & 0xFFFF, body.array()));
    }

    private void scheduleHeartbeat() {
        heartbeatThread = Thread.ofVirtual()
            .name("htim-hb-" + userId)
            .start(() -> {
                while (connected) {
                    try {
                        Thread.sleep(Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS));
                        if (connected) sendHeartbeat();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            });
    }

    private void cancelHeartbeat() {
        Thread t = heartbeatThread;
        heartbeatThread = null;
        if (t != null) t.interrupt();
    }

    private synchronized void send(byte[] frame) {
        WebSocket s = this.ws;
        if (s == null || !connected) return;
        sendChain = sendChain
            .handle((ignoredResult, ignoredEx) -> null)
            .thenCompose(ignored -> s.sendBinary(ByteBuffer.wrap(frame), true))
            .exceptionally(e -> {
                log.warn("ht_im/sock send failed uid={}: {}", userId, e.getMessage());
                return null;
            });
    }

    // ── Inbound ─────────────────────────────────────────────────────────────

    private void handleFrame(byte[] raw) {
        if (raw.length < ImPacket.HEADER_LEN) return;
        ImPacket.Header header = ImPacket.parseHeader(raw);

        switch (header.packetType()) {
            case ImPacket.TYPE_LOGIN_OR_PONG -> handleLoginOrPong(raw, header);
            case ImPacket.TYPE_PUSH_NOTIFY -> handlePushNotify(raw, header);
            default -> { /* typing (0xF5), ack (0xF3) echoes, etc. — not handled */ }
        }
    }

    private void handleLoginOrPong(byte[] raw, ImPacket.Header header) {
        if (header.cmdId() == ImPacket.CMD_PONG) return; // bare pong, nothing to do

        byte[] payload = decompressIfZlib(ImPacket.payloadOf(raw, header));
        String text = new String(payload, StandardCharsets.UTF_8).trim();
        if (!text.startsWith("{")) return;

        JsonNode data;
        try {
            data = om.readTree(text);
        } catch (Exception e) {
            log.warn("ht_im/sock login response parse failed: {}", e.getMessage());
            return;
        }

        JsonNode sessionKeyNode = data.path("data").path("session_key");
        if (!sessionKeyNode.isMissingNode() && !sessionKeyNode.isNull()) {
            this.sessionKey = sessionKeyNode.asText().getBytes(StandardCharsets.UTF_8);
            log.info("ht_im/sock session key captured uid={}", userId);
            emit(new ImEvent.ConnectionState("connected"));
        }

        int status = data.path("status").asInt(0);
        if (status == 2) {
            emit(new ImEvent.AccountStatus("banned"));
            close();
        } else if (status == 105) {
            emit(new ImEvent.AccountStatus("session_mismatch"));
            close();
        }
    }

    private void handlePushNotify(byte[] raw, ImPacket.Header header) {
        send(ImPacket.buildAck(raw, header));

        byte[] key = this.sessionKey;
        byte[] encPayload = ImPacket.payloadOf(raw, header);
        if (key == null || encPayload.length == 0) return;

        byte[] decrypted = QqTeaCipher.decrypt(encPayload, key);
        if (decrypted == null || decrypted.length == 0) return;

        int firstByte = decrypted[0] & 0xFF;
        byte[] finalBytes;
        if (firstByte == 0x78) {
            finalBytes = decompressIfZlib(decrypted);
        } else if (firstByte == 0x7B) {
            finalBytes = decrypted;
        } else {
            // 0x25 (read receipt) / 0x08 (protobuf new-message-notify) — DM-chat sync
            // mechanisms, deliberately out of scope (see class doc).
            return;
        }

        String jsonStr = new String(finalBytes, StandardCharsets.UTF_8).replace("\0", "");
        mapper.map(jsonStr).ifPresent(this::emit);
    }

    private void emit(ImEvent event) {
        Consumer<ImEvent> l = eventListener;
        if (l != null) l.accept(event);
    }

    private static byte[] decompressIfZlib(byte[] data) {
        if (data.length < 2 || (data[0] & 0xFF) != 0x78) return data;
        try {
            Inflater inflater = new Inflater();
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 3);
            byte[] buf = new byte[1024];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0) break;
                out.write(buf, 0, n);
            }
            inflater.end();
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("ht_im/sock zlib inflate failed: {}", e.getMessage());
            return data;
        }
    }

    // ── WebSocket listener ──────────────────────────────────────────────────

    private class Listener implements WebSocket.Listener {

        private ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            buffer.write(chunk, 0, chunk.length);
            if (last) {
                byte[] frame = buffer.toByteArray();
                buffer = new ByteArrayOutputStream();
                handleFrame(frame);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connected = false;
            cancelHeartbeat();
            log.info("ht_im/sock closed uid={} status={} reason={}", userId, statusCode, reason);
            Runnable listener = disconnectListener;
            if (listener != null) listener.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("ht_im/sock error uid={}: {}", userId, error.getMessage());
            emit(new ImEvent.Error("ht_im/sock upstream error: " + error.getMessage()));
        }
    }
}
