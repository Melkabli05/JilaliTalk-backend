package com.jilali.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.crypto.ApkSignatureGenerator;
import com.jilali.crypto.QqTeaCipher;
import com.jilali.im.dto.ImRealtimeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.Inflater;

import static com.jilali.im.HtImPacketFramer.*;

/**
 * Single binary WebSocket connection to HelloTalk's {@code ht_im/sock} upstream.
 * Sends the login packet on connect, keeps a 30-second heartbeat, decrypts 0xF2 push
 * packets with the QQTEA session key received in the 0xF1 login response, and maps them
 * to {@link ImRealtimeEvent}s for downstream subscribers.
 */
class HtImUpstreamConnector implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HtImUpstreamConnector.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String IM_WS_URL = "wss://api-global.hellotalk8.com/ht_im/sock";

    private final long userId;
    private final String jwt;
    private final String deviceId;
    private final String deviceModel;
    private final ObjectMapper om;

    private volatile Consumer<ImRealtimeEvent> eventListener;
    private volatile Runnable disconnectListener;

    private volatile WebSocket ws;
    private volatile boolean connected;
    private volatile byte[] sessionKey;
    private volatile ScheduledFuture<?> heartbeatFuture;
    private volatile ScheduledExecutorService heartbeatScheduler;
    private volatile CompletableFuture<WebSocket> sendChain = CompletableFuture.completedFuture(null);

    HtImUpstreamConnector(long userId, String jwt, String deviceId, String deviceModel, ObjectMapper om) {
        this.userId      = userId;
        this.jwt         = jwt;
        this.deviceId    = deviceId;
        this.deviceModel = deviceModel;
        this.om          = om;
    }

    void attach(Consumer<ImRealtimeEvent> eventListener, Runnable disconnectListener) {
        this.eventListener      = eventListener;
        this.disconnectListener = disconnectListener;
    }

    CompletableFuture<Void> connect() {
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("im-hb").factory());

        log.info("IM WS connecting uid={}", userId);

        return HTTP_CLIENT.newWebSocketBuilder()
            .buildAsync(URI.create(IM_WS_URL + "?userid=" + userId), new Listener())
            .thenAccept(sock -> {
                this.ws        = sock;
                this.connected = true;
                log.info("IM WS connected uid={}", userId);
                sendLoginPacket(sock);
                sock.request(1);
            });
    }

    @Override
    public void close() {
        connected = false;
        cancelHeartbeat();
        sendChain = CompletableFuture.completedFuture(null);
        WebSocket sock = ws;
        if (sock != null) {
            try { sock.sendClose(1000, "normal"); } catch (Exception _) {}
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
                .put("app_version",          "6.3.40(11126,google)")
                .put("background_reconnect",  0)
                .put("channel",              "com.hellotalk.core.app.NihaotalkApplication")
                .put("client_lang",          "English")
                .put("current_version",       394024)
                .put("device_detail",         deviceModel)
                .put("device_id",             deviceId)
                .put("is_version_update",     0)
                .put("net_type",              1)
                .put("os_lang",              "en")
                .put("os_version",           "11")
                .put("terminal_type",         1));
            sendBinary(sock, buildPacket(userId, CMD_LOGIN, payload));
        } catch (Exception e) {
            log.error("IM: failed to build login packet: {}", e.getMessage());
            emit(new ImRealtimeEvent.Error("IM login build failed: " + e.getMessage()));
        }
    }

    // ── packet handling ──────────────────────────────────────────────────────

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

        byte[] raw = copyPayload(data, payloadLen);
        byte[] decompressed = inflate(raw);
        if (decompressed == null) return;

        String text = new String(decompressed, StandardCharsets.UTF_8).replace("\0", "").trim();
        if (!text.startsWith("{")) return;

        try {
            JsonNode root = om.readTree(text);
            if (h.cmdId() == CMD_OFFLINE_RESPONSE) {
                handleOfflineResponse(root);
                return;
            }
            if (h.cmdId() == CMD_GROUP_RESPONSE) {
                handleGroupResponse(root);
                return;
            }
            handleLoginResponse(root);
        } catch (Exception e) {
            log.warn("IM: F1 JSON parse error: {}", e.getMessage());
        }
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
            scheduleHeartbeat();
            // Proactively request offline DMs — two passes matching old frontend onSessionReady
            sendOfflineSyncRequest(0, CMD_OFFLINE_SYNC,      0xF0);
            sendOfflineSyncRequest(0, CMD_OFFLINE_SYNC_PAGE, 0xF2);
        }
    }

    private void handleF2(Header h, byte[] data, int payloadLen) {
        // Always ACK first
        sendBinary(ws, buildAck(data));

        byte[] key = sessionKey;
        if (key == null || payloadLen == 0) return;

        byte[] encPayload = copyPayload(data, payloadLen);
        byte[] decrypted;
        try {
            decrypted = QqTeaCipher.decrypt(encPayload, key);
        } catch (Exception e) {
            log.warn("IM: F2 QQTEA decrypt failed: {}", e.getMessage());
            return;
        }
        if (decrypted == null || decrypted.length == 0) return;

        int firstByte = decrypted[0] & 0xFF;

        // Read receipt (0x25): bytes 2..38 contain msgId
        if (firstByte == 0x25) {
            if (decrypted.length >= 38) {
                String msgId = new String(decrypted, 2, 36, StandardCharsets.UTF_8)
                    .replace("\0", "").trim();
                emit(new ImRealtimeEvent.ReadReceipt(msgId));
            }
            return;
        }

        // 0x08 = "you have new messages" poke — respond with offline sync request
        if (firstByte == 0x08) {
            log.debug("IM: F2 0x08 poke uid={} — triggering offline sync", userId);
            sendOfflineSyncRequest(0, CMD_OFFLINE_SYNC, 0xF0);
            return;
        }

        byte[] finalBytes;
        if (firstByte == 0x78) {
            finalBytes = inflate(decrypted);
            if (finalBytes == null) { log.warn("IM: F2 inflate failed"); return; }
        } else if (firstByte == 0x7B) {
            finalBytes = decrypted; // JSON directly ('{')
        } else {
            // Log hex bytes so we can analyze unknown push types
            StringBuilder hex = new StringBuilder();
            int maxBytes = Math.min(decrypted.length, 64);
            for (int i = 0; i < maxBytes; i++) {
                hex.append(String.format("%02x", decrypted[i] & 0xFF));
                if (i < maxBytes - 1) hex.append(' ');
            }
            log.info("IM: F2 unknown first byte 0x{} uid={} len={} hex=[{}]",
                Integer.toHexString(firstByte), userId, decrypted.length, hex);
            return;
        }

        String jsonStr = new String(finalBytes, StandardCharsets.UTF_8).replace("\0", "");
        try {
            JsonNode root = om.readTree(jsonStr);
            ImRealtimeEvent event = mapPushPayload(root, h);
            log.info("IM: F2 push uid={} notify_type={} msg_type={} mapped={} raw={}",
                userId, root.path("notify_type").asText(null), root.path("msg_type").asText(null),
                event != null ? event.getClass().getSimpleName() : "DROPPED", jsonStr);
            if (event != null) emit(event);
        } catch (Exception e) {
            log.warn("IM: F2 JSON parse error: {}", e.getMessage());
        }
    }

    private void handleTyping(Header h, byte[] data, int payloadLen) {
        if (h.cmdId() != CMD_TYPING) return;

        byte[] payload = copyPayload(data, payloadLen);
        byte[] key = sessionKey;
        if (key != null && h.keyType() == 1 && payload.length > 0) {
            try {
                byte[] dec = QqTeaCipher.decrypt(payload, key);
                if (dec != null) payload = dec;
            } catch (Exception _) {}
        }
        if (payload.length > 0 && (payload[0] & 0xFF) == 0x78) {
            byte[] inflated = inflate(payload);
            if (inflated != null) payload = inflated;
        }

        // status at LE uint16 offset 4: 1 = typing, 0 = stopped
        boolean isTyping = payload.length < 6 || ((payload[4] & 0xFF) | ((payload[5] & 0xFF) << 8)) == 1;
        emit(new ImRealtimeEvent.TypingIndicator(String.valueOf(h.fromId()), isTyping));
    }

    // ── offline message sync ─────────────────────────────────────────────────

    private void sendOfflineSyncRequest(long lastId, int cmdId, int flag) {
        try {
            byte[] jsonBody = om.writeValueAsBytes(om.createObjectNode().put("last_id", lastId));
            byte[] compressed = deflate(jsonBody);
            sendBinary(ws, buildPacket(userId, cmdId, flag, compressed));
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
            ImRealtimeEvent event = decodeOfflinePacket(b64);
            if (event != null) { emit(event); emitted++; }
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

    private ImRealtimeEvent decodeOfflinePacket(String base64str) {
        try {
            byte[] raw = Base64.getDecoder().decode(base64str);
            if (raw.length < HEADER_LEN) return null;

            ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            int keyType  = buf.get(2) & 0xFF;
            int cmdId    = buf.getShort(4) & 0xFFFF;
            long fromId  = buf.getInt(8) & 0xFFFFFFFFL;
            long toId    = buf.getInt(12) & 0xFFFFFFFFL;
            int bodyLen  = Math.max(0, Math.min(buf.getInt(16), raw.length - HEADER_LEN));

            byte[] payload = new byte[bodyLen];
            System.arraycopy(raw, HEADER_LEN, payload, 0, bodyLen);
            if (payload.length == 0) return null;

            // Decrypt if encrypted
            byte[] key = sessionKey;
            if (keyType == 1 && key != null) {
                try {
                    byte[] dec = QqTeaCipher.decrypt(payload, key);
                    if (dec != null && dec.length > 0) payload = dec;
                } catch (Exception _) {}
            }

            int firstByte = payload[0] & 0xFF;

            // Read receipt
            if (firstByte == 0x25) {
                if (payload.length >= 38) {
                    String msgId = new String(payload, 2, 36, StandardCharsets.UTF_8)
                        .replace("\0", "").trim();
                    return new ImRealtimeEvent.ReadReceipt(msgId);
                }
                return null;
            }

            byte[] finalBytes;
            if (firstByte == 0x78) {
                finalBytes = inflate(payload);
                if (finalBytes == null) return null;
            } else if (firstByte == 0x7B) {
                finalBytes = payload;
            } else {
                return null;
            }

            String jsonStr = new String(finalBytes, StandardCharsets.UTF_8)
                .replace("\0", "").trim();
            if (!jsonStr.startsWith("{")) return null;

            JsonNode msgRoot = om.readTree(jsonStr);
            Header fakeHeader = new Header(PKT_PUSH, keyType, cmdId, 0, fromId, toId, bodyLen);
            ImRealtimeEvent event = mapPushPayload(msgRoot, fakeHeader);
            log.info("IM: offline packet uid={} notify_type={} msg_type={} mapped={} raw={}",
                userId, msgRoot.path("notify_type").asText(null), msgRoot.path("msg_type").asText(null),
                event != null ? event.getClass().getSimpleName() : "DROPPED", jsonStr);
            return event;
        } catch (Exception e) {
            log.debug("IM: decodeOfflinePacket error: {}", e.getMessage());
            return null;
        }
    }

    // ── JSON → ImRealtimeEvent mapping ──────────────────────────────────────

    ImRealtimeEvent mapPushPayload(JsonNode root, Header h) {
        if (root.has("msg_type")) {
            return switch (root.path("msg_type").asText()) {
                case "text"              -> mapText(root, h);
                case "image"             -> mapImage(root, h);
                case "gift"              -> mapGift(root, h);
                case "introduction"      -> mapIntro(root, h);
                case "new_voice_visitor" -> mapProfileVisit(root);
                default                  -> null;
            };
        }
        if (root.has("notify_type")) return mapNotify(root, h);
        return null;
    }

    private ImRealtimeEvent mapText(JsonNode root, Header h) {
        String fromId = textOr(root, "from_id", String.valueOf(h.fromId()));
        JsonNode t = root.path("text");
        String text = t.isObject() ? textOr(t, "text", "") : t.asText("");
        long ts = root.path("ts").asLong(System.currentTimeMillis());
        return new ImRealtimeEvent.TextMessage(fromId, text, ts);
    }

    private ImRealtimeEvent mapImage(JsonNode root, Header h) {
        String fromId = textOr(root, "from_id", String.valueOf(h.fromId()));
        String url = root.path("image").path("url").asText("");
        if (url.isBlank()) url = textOr(root, "image_url", "");
        long ts = root.path("ts").asLong(System.currentTimeMillis());
        return new ImRealtimeEvent.ImageMessage(fromId, url, ts);
    }

    private ImRealtimeEvent mapGift(JsonNode root, Header h) {
        String fromId       = textOr(root, "from_id", String.valueOf(h.fromId()));
        String fromNickname = textOr(root, "from_nickname", textOr(root, "nickname", ""));
        long   giftId       = root.path("gift_id").asLong(0);
        int    count        = root.path("gift_number").asInt(1);
        return new ImRealtimeEvent.GiftMessage(fromId, fromNickname, giftId, count);
    }

    private ImRealtimeEvent mapIntro(JsonNode root, Header h) {
        String fromId       = textOr(root, "from_id", String.valueOf(h.fromId()));
        String fromNickname = textOr(root, "from_nickname", textOr(root, "nickname", ""));
        return new ImRealtimeEvent.IntroductionMessage(fromId, fromNickname);
    }

    private ImRealtimeEvent mapNotify(JsonNode root, Header h) {
        // Room share: has cname at the top level (distinct from notify_info.cname below)
        if (root.has("cname")) {
            String cname        = textOr(root, "cname", "");
            String fromNickname = textOr(root, "from_nickname", textOr(root, "nickname", ""));
            String headUrl      = root.path("head_url").isNull() ? null : textOr(root, "head_url", null);
            if (root.has("count") || root.has("voice_count")) {
                int count = root.has("count")
                    ? root.path("count").asInt(0)
                    : root.path("voice_count").asInt(0);
                return new ImRealtimeEvent.VoiceRoomShared(fromNickname, cname, headUrl, count);
            }
            return new ImRealtimeEvent.LiveRoomShared(fromNickname, cname, headUrl);
        }

        // Personal room notify_type pushes, received here on the same ht_im/sock channel with
        // notify_info nested (no "event" wrapper, unlike the per-room LiveHub frames handled by
        // HtNotifyMapper). Unlike the LiveHub broadcast shape, these carry no user_id at all —
        // confirmed from a live capture of a real notify_type 48 push:
        //   {"notify_type":48,"notify_info":{"cname":"VR_...","host_id":131331894}}
        // There's no point telling an account who was invited on its own personal channel — it's
        // implicitly this connector's own userId — so that's the fallback when the field is absent.
        JsonNode info = root.path("notify_info");
        String selfId = String.valueOf(userId);
        switch (root.path("notify_type").asText("")) {
            case "18":
                return new ImRealtimeEvent.StageInvite(textOr(info, "user_id", selfId), textOr(info, "cname", ""));
            case "48":
                return new ImRealtimeEvent.ModInvite(textOr(info, "user_id", selfId), textOr(info, "cname", ""));
            case "34":
                return new ImRealtimeEvent.ModAccepted(textOr(info, "user_id", selfId));
            case "35":
                return new ImRealtimeEvent.ModRemoved(textOr(info, "user_id", selfId));
            case "40":
                return new ImRealtimeEvent.ModUnmuted(textOr(info, "user_id", selfId));
            case "53":
                return new ImRealtimeEvent.Follow(textOr(info, "nickname", ""), info.path("status").asInt(0));
            default:
                break;
        }

        // Profile visit: has visitor_uid / visitor_user_id / visitor_id (older/alternate shape)
        for (String field : new String[]{"visitor_uid", "visitor_user_id", "visitor_id"}) {
            if (root.has(field)) {
                return new ImRealtimeEvent.ProfileVisit(textOr(root, field, ""));
            }
        }

        return null;
    }

    private ImRealtimeEvent mapProfileVisit(JsonNode root) {
        // scriptv2.js startwebsock(): msg_type === "new_voice_visitor" carries a top-level userId.
        String visitorId = textOr(root, "userId", textOr(root, "user_id", ""));
        return visitorId.isEmpty() ? null : new ImRealtimeEvent.ProfileVisit(visitorId);
    }

    // ── heartbeat ────────────────────────────────────────────────────────────

    private void scheduleHeartbeat() {
        heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(
            this::sendPing, 30, 30, TimeUnit.SECONDS);
    }

    private void sendPing() {
        if (!connected) return;
        sendBinary(ws, buildHeartbeat(userId));
    }

    private void cancelHeartbeat() {
        ScheduledFuture<?> f = heartbeatFuture;
        if (f != null) { f.cancel(false); heartbeatFuture = null; }
        ScheduledExecutorService s = heartbeatScheduler;
        if (s != null) { s.shutdownNow(); heartbeatScheduler = null; }
    }

    // ── send ─────────────────────────────────────────────────────────────────

    private void sendBinary(WebSocket sock, byte[] data) {
        if (sock == null || !connected) return;
        ByteBuffer buf = ByteBuffer.wrap(data);
        sendChain = sendChain
            .handle((_, _) -> null)
            .thenCompose(_ -> sock.sendBinary(buf, true))
            .exceptionally(e -> {
                log.warn("IM WS send failed uid={}: {}", userId, e.getMessage());
                return null;
            });
    }

    private void emit(ImRealtimeEvent event) {
        Consumer<ImRealtimeEvent> l = eventListener;
        if (l != null) l.accept(event);
    }

    // ── utilities ────────────────────────────────────────────────────────────

    private static byte[] copyPayload(byte[] data, int payloadLen) {
        byte[] payload = new byte[payloadLen];
        System.arraycopy(data, HEADER_LEN, payload, 0, payloadLen);
        return payload;
    }

    private static byte[] inflate(byte[] data) {
        if (data == null || data.length == 0) return data;
        if ((data[0] & 0xFF) != 0x78) return data; // not zlib compressed

        // Try standard zlib inflate, then raw inflate
        for (boolean nowrap : new boolean[]{false, true}) {
            try {
                Inflater inf = new Inflater(nowrap);
                inf.setInput(data);
                byte[] out = new byte[data.length * 8];
                int n = inf.inflate(out);
                inf.end();
                byte[] result = new byte[n];
                System.arraycopy(out, 0, result, 0, n);
                return result;
            } catch (Exception _) {}
        }
        return null;
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : fallback;
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
            cancelHeartbeat();
            log.info("IM WS closed uid={} status={} reason={}", userId, statusCode, reason);
            Runnable l = disconnectListener;
            if (l != null) l.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.warn("IM WS error uid={}: {}", userId, error.getMessage());
            emit(new ImRealtimeEvent.Error("IM upstream error: " + error.getMessage()));
        }
    }
}
