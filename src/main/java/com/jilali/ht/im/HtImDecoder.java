package com.jilali.ht.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Decodes raw binary frames from HelloTalk's IM WebSocket into typed {@link HtImEvent}s.
 *
 * <p>Mirrors the {@code ws.onmessage} handler in {@code connectwebsock.js}.
 *
 * <p>Frame types handled:
 * <ul>
 *   <li><b>0xF1</b> — server responses: login, heartbeat PONG, offline/group message sync</li>
 *   <li><b>0xF2</b> — push notifications: QQTEA decrypt → optional inflate → JSON dispatch</li>
 *   <li><b>0xF5 + cmdId 16407</b> — typing indicator</li>
 * </ul>
 *
 * <p>Thread-safe: stateless; session key is passed per-call.
 */
@Singleton
public class HtImDecoder {

    private static final Logger log = LoggerFactory.getLogger(HtImDecoder.class);

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        Pattern.CASE_INSENSITIVE);

    private static final int CMD_PONG         = 0x9002;
    private static final int CMD_OFFLINE_SYNC = 16454;
    private static final int CMD_GROUP_SYNC   = 29968;
    private static final int CMD_MSG_ACK      = 16386;
    private static final int CMD_TYPING       = 16407;

    private final ObjectMapper om;

    public HtImDecoder(ObjectMapper om) {
        this.om = om;
    }

    /**
     * Decodes a raw binary frame.
     *
     * @param raw        the bytes received from the HT IM WebSocket
     * @param sessionKey the current QQTEA session key, or {@code null} before login
     * @return a typed event, or {@link Optional#empty()} if the frame should be silently ignored
     */
    public Optional<HtImEvent> decode(byte[] raw, byte[] sessionKey) {
        HtImPacket pkt = HtImPacket.parse(raw);
        if (pkt == null) {
            log.trace("IM: frame too short ({}b) — ignored", raw != null ? raw.length : 0);
            return Optional.empty();
        }

        return switch (pkt.flag()) {
            case 0xF1 -> decodeF1(pkt);
            case 0xF2 -> decodeF2(pkt, sessionKey);
            case 0xF5 -> decodeF5(pkt, sessionKey);
            default -> {
                log.trace("IM: unknown flag 0x{} — raw pass-through", Integer.toHexString(pkt.flag()));
                yield Optional.of(new HtImEvent.Raw(pkt.flag(), pkt.cmdId(), null));
            }
        };
    }

    // ---- 0xF1 server responses ----------------------------------------------

    private Optional<HtImEvent> decodeF1(HtImPacket pkt) {
        if (pkt.cmdId() == CMD_PONG) {
            return Optional.of(new HtImEvent.Pong());
        }

        byte[] payload = pkt.payload();
        if (payload.length == 0) return Optional.empty();

        if ((payload[0] & 0xFF) == 0x78) {
            payload = inflate(payload, false);
            if (payload == null) {
                log.warn("IM 0xF1: inflate failed (cmdId={})", pkt.cmdId());
                return Optional.empty();
            }
        }

        String text = new String(payload, StandardCharsets.UTF_8).trim();
        if (!text.startsWith("{")) {
            log.trace("IM 0xF1: non-JSON payload (cmdId={})", pkt.cmdId());
            return Optional.empty();
        }

        try {
            JsonNode root = om.readTree(text);

            if (pkt.cmdId() == CMD_OFFLINE_SYNC) {
                long lastId = root.path("data").path("last_id").asLong(0);
                return Optional.of(new HtImEvent.OfflineMessages(toObject(root), lastId));
            }
            if (pkt.cmdId() == CMD_GROUP_SYNC) {
                return Optional.of(new HtImEvent.GroupMessages(toObject(root)));
            }

            // Login response: data.session_key present
            JsonNode data      = root.path("data");
            String sessionKey  = data.path("session_key").asText(null);
            String sessionId   = data.path("session_id").asText("");
            String newJwt      = data.path("jwt").asText("");
            String areaCode    = data.path("area_code").asText("");
            String userId      = data.path("userid").asText(String.valueOf(pkt.fromId()));

            if (sessionKey != null) {
                return Optional.of(new HtImEvent.SessionReady(sessionId, userId, newJwt, areaCode, sessionKey));
            }

            return Optional.of(new HtImEvent.Raw(pkt.flag(), pkt.cmdId(), toObject(root)));
        } catch (Exception e) {
            log.warn("IM 0xF1: JSON parse error (cmdId={}): {}", pkt.cmdId(), e.getMessage());
            return Optional.of(new HtImEvent.Error("0xF1 parse error: " + e.getMessage()));
        }
    }

    // ---- 0xF2 push notifications --------------------------------------------

    private Optional<HtImEvent> decodeF2(HtImPacket pkt, byte[] sessionKey) {
        byte[] payload = pkt.payload();
        if (payload.length == 0) return Optional.empty();

        if (pkt.cmdId() == CMD_MSG_ACK) {
            payload = maybeDecryptAndInflate(payload, pkt.keyType(), sessionKey);
            if (payload == null) return Optional.empty();
            return Optional.of(new HtImEvent.MsgAck("", new String(payload, StandardCharsets.UTF_8)));
        }

        payload = maybeDecryptAndInflate(payload, pkt.keyType(), sessionKey);
        if (payload == null || payload.length == 0) return Optional.empty();

        int firstByte = payload[0] & 0xFF;

        // 0x25 = read receipt: msgId at bytes [2, 38)
        if (firstByte == 0x25) {
            byte[] msgIdBytes = Arrays.copyOfRange(payload, 2, Math.min(38, payload.length));
            String msgId = new String(msgIdBytes, StandardCharsets.UTF_8).replace("\0", "").trim();
            return Optional.of(new HtImEvent.ReadReceipt(msgId, pkt.fromId()));
        }

        // 0x08 = protobuf new-message notify — extract UUID for the frontend to use
        if (firstByte == 0x08) {
            log.debug("IM 0xF2: protobuf new-message notify (fromId={})", pkt.fromId());
            String msgId = extractUuid(payload);
            return Optional.of(new HtImEvent.DirectMessage(
                "new_message_notify", String.valueOf(pkt.fromId()), null, msgId, null));
        }

        // 0x7B = '{' — JSON payload
        String text = new String(payload, StandardCharsets.UTF_8).replace("\0", "").trim();
        if (!text.startsWith("{")) {
            log.trace("IM 0xF2: non-JSON first byte 0x{} — ignored", Integer.toHexString(firstByte));
            return Optional.empty();
        }

        try {
            JsonNode root = om.readTree(text);
            String msgType = root.has("msg_type")    ? root.get("msg_type").asText()
                           : root.has("notify_type") ? root.get("notify_type").asText()
                           : "unknown";
            String fromId = root.path("from_id").asText(String.valueOf(pkt.fromId()));
            String toId   = root.path("to_id").asText(String.valueOf(pkt.toId()));
            String msgId  = root.path("msg_id").asText(root.path("msg").path("msg_id").asText(""));
            return Optional.of(new HtImEvent.DirectMessage(msgType, fromId, toId, msgId, toObject(root)));
        } catch (Exception e) {
            log.warn("IM 0xF2: JSON parse error: {}", e.getMessage());
            return Optional.of(new HtImEvent.Error("0xF2 parse error: " + e.getMessage()));
        }
    }

    // ---- 0xF5 typing indicator ----------------------------------------------

    private Optional<HtImEvent> decodeF5(HtImPacket pkt, byte[] sessionKey) {
        if (pkt.cmdId() != CMD_TYPING) return Optional.empty();

        byte[] payload = maybeDecryptAndInflate(pkt.payload(), pkt.keyType(), sessionKey);
        if (payload == null) payload = pkt.payload();

        // uid(4 LE) + status(2 LE); status=1 → typing
        boolean isTyping = true;
        if (payload.length >= 6) {
            ByteBuffer buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
            buf.getInt(); // skip uid
            isTyping = (buf.getShort() & 0xFFFF) == 1;
        }
        return Optional.of(new HtImEvent.Typing(pkt.fromId(), isTyping));
    }

    // ---- Helpers ------------------------------------------------------------

    /**
     * Decrypts with QQTEA if {@code keyType=1} and {@code sessionKey} is set,
     * then inflates if the first byte is 0x78 (zlib magic byte).
     *
     * @return decoded payload, or {@code null} if decryption failed
     */
    byte[] maybeDecryptAndInflate(byte[] payload, int keyType, byte[] sessionKey) {
        if (keyType == 1 && sessionKey != null && payload.length > 0) {
            payload = QqTea.decrypt(payload, sessionKey);
            if (payload == null || payload.length == 0) {
                log.warn("IM: QQTEA decrypt returned empty");
                return null;
            }
        }
        if (payload != null && payload.length > 0 && (payload[0] & 0xFF) == 0x78) {
            payload = inflate(payload, false);
        }
        return payload;
    }

    /**
     * Inflates zlib-compressed bytes using {@link InflaterInputStream}.
     * Falls back to raw deflate (no zlib header) if standard zlib fails.
     *
     * @param nowrap {@code true} for raw deflate (no zlib header), {@code false} for zlib
     */
    static byte[] inflate(byte[] data, boolean nowrap) {
        byte[] result = inflateWith(data, nowrap);
        return result != null ? result : inflateWith(data, !nowrap);
    }

    private static byte[] inflateWith(byte[] data, boolean nowrap) {
        try (var iis = new InflaterInputStream(
                new ByteArrayInputStream(data), new Inflater(nowrap))) {
            byte[] result = iis.readAllBytes();
            return result.length > 0 ? result : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Object toObject(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try { return om.treeToValue(node, Object.class); } catch (Exception e) { return node.toString(); }
    }

    private static String extractUuid(byte[] data) {
        String text = new String(data, StandardCharsets.UTF_8);
        var m = UUID_PATTERN.matcher(text);
        return m.find() ? m.group() : "";
    }
}
