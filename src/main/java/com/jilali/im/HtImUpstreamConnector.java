// src/main/java/com/jilali/im/HtImUpstreamConnector.java
package com.jilali.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.ws.ExponentialBackoff;
import com.jilali.core.ws.HeartbeatPump;
import com.jilali.core.ws.SequentialSender;
import com.jilali.crypto.ApkSignatureGenerator;
import com.jilali.im.HtImFrameDecoder.F2Push;
import com.jilali.im.dto.ImRealtimeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.jilali.im.HtImPacketFramer.*;

/**
 * Single binary WebSocket connection to HelloTalk's {@code ht_im/sock} upstream.
 * Sends the login packet on connect, keeps a 30-second heartbeat, decrypts 0xF2 push
 * packets with the QQTEA session key received in the 0xF1 login response, and maps them
 * to {@link ImRealtimeEvent}s for downstream subscribers.
 *
 * <p>Byte-level decoding lives in {@link HtImFrameDecoder}; JSON-to-event mapping lives in
 * {@link HtImNotifyMapper}. This class owns only the WebSocket lifecycle, reconnection, and
 * dispatch between them. An unexpected close (network drop, not our own {@link #close()})
 * triggers an internal reconnect loop with capped exponential backoff — {@link #connect()}'s
 * returned future only ever reflects the first attempt, so {@code ImEventSource}'s existing
 * failure handling for "upstream unreachable from the start" is unaffected. This reconnect
 * loop is the only one in play here — {@code ImEventSource} does not itself retry, so a
 * future change adding retry there too would stack two independent backoff loops.
 */
class HtImUpstreamConnector implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HtImUpstreamConnector.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String IM_WS_URL = "wss://api-global.hellotalk8.com/ht_im/sock";
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    private final long userId;
    private final String jwt;
    private final String deviceId;
    private final String deviceModel;
    private final ObjectMapper om;
    private final HtImFrameDecoder decoder;
    private final HtImNotifyMapper notifyMapper;

    private final SequentialSender sender = new SequentialSender();
    private final HeartbeatPump heartbeat = new HeartbeatPump("im-hb");
    private final ExponentialBackoff backoff = new ExponentialBackoff(Duration.ofSeconds(1), Duration.ofSeconds(30));

    private volatile Consumer<ImRealtimeEvent> eventListener;
    private volatile Runnable disconnectListener;

    private volatile WebSocket ws;
    private volatile boolean connected;
    private volatile boolean intentionalClose;
    private volatile byte[] sessionKey;

    HtImUpstreamConnector(long userId, String jwt, String deviceId, String deviceModel, ObjectMapper om) {
        this.userId       = userId;
        this.jwt          = jwt;
        this.deviceId     = deviceId;
        this.deviceModel  = deviceModel;
        this.om           = om;
        this.decoder      = new HtImFrameDecoder(om);
        this.notifyMapper = new HtImNotifyMapper(userId);
    }

    void attach(Consumer<ImRealtimeEvent> eventListener, Runnable disconnectListener) {
        this.eventListener      = eventListener;
        this.disconnectListener = disconnectListener;
    }

    CompletableFuture<Void> connect() {
        this.intentionalClose = false;
        log.info("IM WS connecting uid={}", userId);
        return attemptConnect();
    }

    private CompletableFuture<Void> attemptConnect() {
        return HTTP_CLIENT.newWebSocketBuilder()
            .buildAsync(URI.create(IM_WS_URL + "?userid=" + userId), new Listener())
            .thenAccept(sock -> {
                if (intentionalClose) {
                    try { sock.sendClose(1000, "normal"); } catch (Exception ignored) {}
                    heartbeat.close();
                    return;
                }
                this.ws        = sock;
                this.connected = true;
                backoff.reset();
                log.info("IM WS connected uid={}", userId);
                sendLoginPacket(sock);
                sock.request(1);
            });
    }

    private void reconnectInBackground() {
        if (intentionalClose) return;
        Duration delay = backoff.nextDelay();
        log.info("IM WS reconnecting uid={} in {}ms", userId, delay.toMillis());
        CompletableFuture.runAsync(() -> {
            if (intentionalClose) return;
            attemptConnect().exceptionally(ex -> {
                log.warn("IM WS reconnect attempt failed uid={}: {}", userId, ex.getMessage());
                reconnectInBackground();
                return null;
            });
        }, CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS));
    }

    @Override
    public void close() {
        intentionalClose = true;
        connected = false;
        heartbeat.close();
        sender.reset();
        WebSocket sock = ws;
        if (sock != null) {
            try { sock.sendClose(1000, "normal"); } catch (Exception ignored) {}
        }
    }

    // ── login ────────────────────────────────────────────────────────────────

    private void sendLoginPacket(WebSocket sock) {
        try {
            long ts = System.currentTimeMillis();
            String apkSig = ApkSignatureGenerator.generate(deviceId, ts);
            var payload = om.writeValueAsBytes(om.createObjectNode()
                .put("jwt",                  jwt)
                .put("mobile_operator",      "Orange")
                .put("operator_country",     "ma")
                .put("android_apk_signature", apkSig)
                .put("app_version",          ApkSignatureGenerator.VERSION_NAME + "(" + ApkSignatureGenerator.VERSION_CODE + ",google)")
                .put("background_reconnect",  0)
                .put("channel",              "com.hellotalk.core.app.NihaotalkApplication")
                .put("client_lang",          "English")
                .put("current_version",       ApkSignatureGenerator.VERSION_CODE)
                .put("device_detail",         deviceModel)
                .put("device_id",             deviceId)
                .put("is_version_update",     0)
                .put("net_type",              1)
                .put("os_lang",              "en")
                .put("os_version",           "11")
                .put("terminal_type",         1));
            sendBinary(buildPacket(userId, CMD_LOGIN, payload));
        } catch (Exception e) {
            log.error("IM: failed to build login packet: {}", e.getMessage());
            emit(new ImRealtimeEvent.Error("IM login build failed: " + e.getMessage()));
        }
    }

    // ── packet dispatch ──────────────────────────────────────────────────────

    private void handlePacket(byte[] data) {
        Header h = parseHeader(data);
        if (h == null) return;

        int payloadLen = Math.min(h.payloadLen(), Math.max(0, data.length - HEADER_LEN));

        switch (h.packetType()) {
            case PKT_RESPONSE -> handleF1(h, data, payloadLen);
            case PKT_PUSH     -> handleF2(h, data, payloadLen);
            case PKT_TYPING   -> handleTyping(h, data, payloadLen);
            default           -> log.trace("IM: unknown packet type 0x{}", Integer.toHexString(h.packetType()));
        }
    }

    private void handleF1(Header h, byte[] data, int payloadLen) {
        if (h.cmdId() == CMD_PONG) {
            log.trace("IM: heartbeat pong uid={}", userId);
            return;
        }

        Optional<JsonNode> root = decoder.decodeF1(data, payloadLen);
        root.ifPresent(json -> {
            if (h.cmdId() == CMD_OFFLINE_RESPONSE) {
                handleOfflineResponse(json);
            } else if (h.cmdId() == CMD_GROUP_RESPONSE) {
                handleGroupResponse(json);
            } else {
                handleLoginResponse(json);
            }
        });
    }

    private void handleLoginResponse(JsonNode root) {
        int status = root.path("status").asInt(0);
        if (status == 2) {
            log.warn("IM: account banned uid={}", userId);
            emit(new ImRealtimeEvent.AccountStatus("banned"));
            close();
            return;
        }
        if (status == 105) {
            log.warn("IM: session id mismatch uid={}", userId);
            emit(new ImRealtimeEvent.AccountStatus("session_mismatch"));
            close();
            return;
        }

        JsonNode data = root.path("data");
        if (data.has("session_key")) {
            String key = data.path("session_key").asText();
            this.sessionKey = key.getBytes(StandardCharsets.UTF_8);
            log.info("IM: session key captured uid={}", userId);
            emit(new ImRealtimeEvent.ConnectionState("connected"));
            heartbeat.start(HEARTBEAT_INTERVAL, this::sendPing);
            // Proactively request offline DMs — two passes matching old frontend onSessionReady
            sendOfflineSyncRequest(0, CMD_OFFLINE_SYNC,      0xF0);
            sendOfflineSyncRequest(0, CMD_OFFLINE_SYNC_PAGE, 0xF2);
        }
    }

    private void handleF2(Header h, byte[] data, int payloadLen) {
        // Always ACK first
        sendBinary(buildAck(data));

        // MSG-ACK (cmdId 16386) is the upstream's echo confirming it accepted an outbound
        // DM we sent. Body is short binary ([u16 strLen][strVal UTF-8][u64 LE sequence]
        // per connectwebsock.js decodeCmd16386) — NOT a JSON push. Route it before the
        // first-byte decoder to avoid the JSON-discard path that notifyMapper would hit.
        if (h.cmdId() == CMD_MSG_ACK) {
            byte[] body = HtImPacketFramer.copyPayload(data, payloadLen);
            decoder.decodeMsgAck(body).ifPresent(ack -> {
                log.info("IM: MSG-ACK msgId={} sequence={} prefix=0x{}", ack.msgId(), ack.sequence(),
                    Integer.toHexString(ack.prefix()));
                emit(new ImRealtimeEvent.MessageAck(ack.msgId(), ack.sequence(), ack.prefix()));
            });
            return;
        }

        F2Push push = decoder.decodeF2(data, payloadLen, sessionKey);
        dispatchPush(push, h);
    }

    /** @return true if this push resulted in an emitted {@link ImRealtimeEvent}. */
    private boolean dispatchPush(F2Push push, Header h) {
        switch (push) {
            case F2Push.Receipt r -> {
                emit(new ImRealtimeEvent.ReadReceipt(r.msgId()));
                return true;
            }
            case F2Push.Poke ignored -> {
                log.debug("IM: F2 0x08 poke uid={} — triggering offline sync", userId);
                sendOfflineSyncRequest(0, CMD_OFFLINE_SYNC, 0xF0);
                return false;
            }
            case F2Push.Json j -> {
                ImRealtimeEvent event = notifyMapper.map(j.root(), h);
                log.info("IM: F2 push uid={} notify_type={} msg_type={} mapped={} raw={}",
                    userId, j.root().path("notify_type").asText(null), j.root().path("msg_type").asText(null),
                    event != null ? event.getClass().getSimpleName() : "DROPPED", j.root());
                if (event != null) { emit(event); return true; }
                return false;
            }
            case F2Push.Unknown u -> {
                log.info("IM: F2 unknown first byte 0x{} uid={} len={} hex=[{}]",
                    Integer.toHexString(u.firstByte()), userId, u.bytes().length, toHex(u.bytes()));
                return false;
            }
            case F2Push.DecryptFailed ignored -> {
                log.warn("IM: F2 QQTEA decrypt failed uid={}", userId);
                return false;
            }
            case F2Push.Ignored ignored -> {
                return false;
            }
        }
    }

    private void handleTyping(Header h, byte[] data, int payloadLen) {
        if (h.cmdId() != CMD_TYPING) return;
        boolean isTyping = decoder.decodeTypingStatus(data, payloadLen, sessionKey, h.keyType());
        emit(new ImRealtimeEvent.TypingIndicator(String.valueOf(h.fromId()), isTyping));
    }

    // ── offline message sync ─────────────────────────────────────────────────

    private void sendOfflineSyncRequest(long lastId, int cmdId, int flag) {
        try {
            byte[] jsonBody = om.writeValueAsBytes(om.createObjectNode().put("last_id", lastId));
            byte[] compressed = deflate(jsonBody);
            sendBinary(buildPacket(userId, cmdId, flag, compressed));
            log.info("IM: sent offline sync cmdId={} flag=0x{} last_id={}", cmdId, Integer.toHexString(flag), lastId);
        } catch (Exception e) {
            log.warn("IM: failed to send offline sync request: {}", e.getMessage());
        }
    }

    private void handleOfflineResponse(JsonNode root) {
        log.info("IM: offline raw response uid={}: {}", userId, root);
        JsonNode data = root.path("data");
        JsonNode packetList = data.path("packet_list");
        if (!packetList.isArray()) {
            log.info("IM: offline response has no packet_list (code={})", root.path("code").asInt(-1));
            return;
        }

        int emitted = 0;
        for (JsonNode item : packetList) {
            String b64 = item.asText(null);
            if (b64 == null || b64.isEmpty()) continue;
            Optional<HtImFrameDecoder.OfflinePacket> offline = decoder.decodeOfflinePacket(b64, sessionKey);
            if (offline.isPresent() && dispatchPush(offline.get().body(), offline.get().header())) {
                emitted++;
            }
        }
        log.info("IM: offline response {} packets, emitted {} events uid={}", packetList.size(), emitted, userId);

        // Pagination: if the server returned items there may be more
        long nextLastId = data.path("last_id").asLong(0);
        if (packetList.size() > 0 && nextLastId > 0) {
            sendOfflineSyncRequest(nextLastId, CMD_OFFLINE_SYNC_PAGE, 0xF2);
        }
    }

    private void handleGroupResponse(JsonNode root) {
        JsonNode msgs = root.path("data").path("msgs");
        if (!msgs.isArray()) {
            log.info("IM: group sync response no msgs (code={})", root.path("code").asInt(-1));
            return;
        }
        int emitted = 0;
        for (JsonNode msg : msgs) {
            String senderId   = textOr(msg, "sender_id", "");
            String senderName = textOr(msg, "sender_name", senderId);
            String roomName   = textOr(msg, "room_name", textOr(msg, "cname", ""));
            String text       = msg.path("text").path("text").asText(
                                    msg.path("text").asText(""));
            if (!senderId.isEmpty()) {
                emit(new ImRealtimeEvent.GroupMessage(senderId, senderName, roomName, text));
                emitted++;
            }
        }
        log.info("IM: group sync response {} msgs, emitted {} uid={}", msgs.size(), emitted, userId);
    }

    // ── heartbeat / send ─────────────────────────────────────────────────────

    private void sendPing() {
        if (connected) sendBinary(buildHeartbeat(userId));
    }

    private void sendBinary(byte[] data) {
        sendOutbound(data);
    }

    /**
     * Send a pre-built packet upstream through the existing IM WS, honoring the {@link SequentialSender}
     * (so partial writes don't interleave) and silently dropping when the socket isn't connected
     * (so a 1:1 send during a transient reconnect window doesn't error the HTTP caller — they'll
     * catch the issue on the next retry or via the upstream's own echo).
     */
    void sendOutbound(byte[] data) {
        WebSocket sock = this.ws;
        if (sock == null || !connected) return;
        ByteBuffer buf = ByteBuffer.wrap(data);
        sender.enqueue(() -> sock.sendBinary(buf, true),
            e -> log.warn("IM WS send failed uid={}: {}", userId, e.getMessage()));
    }

    private void emit(ImRealtimeEvent event) {
        Consumer<ImRealtimeEvent> l = eventListener;
        if (l != null) l.accept(event);
    }

    // ── utilities ────────────────────────────────────────────────────────────

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : fallback;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        int maxBytes = Math.min(bytes.length, 64);
        for (int i = 0; i < maxBytes; i++) {
            hex.append(String.format("%02x", bytes[i] & 0xFF));
            if (i < maxBytes - 1) hex.append(' ');
        }
        return hex.toString();
    }

    // ── WebSocket.Listener ───────────────────────────────────────────────────

    private class Listener implements WebSocket.Listener {

        private final java.io.ByteArrayOutputStream binaryBuf = new java.io.ByteArrayOutputStream(4096);

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            binaryBuf.write(chunk, 0, chunk.length);
            if (last) {
                byte[] frame = binaryBuf.toByteArray();
                binaryBuf.reset();
                handlePacket(frame);
            }
            ws.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            connected = false;
            heartbeat.stop();
            log.info("IM WS closed uid={} status={} reason={}", userId, statusCode, reason);
            if (intentionalClose) {
                Runnable l = disconnectListener;
                if (l != null) l.run();
            } else {
                reconnectInBackground();
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.warn("IM WS error uid={}: {}", userId, error.getMessage());
            emit(new ImRealtimeEvent.Error("IM upstream error: " + error.getMessage()));
        }
    }
}
