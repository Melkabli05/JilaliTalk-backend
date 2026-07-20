package com.jilali.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.crypto.QqTeaCipher;
import com.jilali.im.HtImPacketFramer.Header;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import static com.jilali.im.HtImPacketFramer.HEADER_LEN;
import static com.jilali.im.HtImPacketFramer.PKT_PUSH;

/**
 * Pure byte-level decoding for ht_im/sock frames: QQTEA decrypt, zlib inflate, and JSON
 * extraction. No networking and no mutable state beyond a per-instance ObjectMapper.
 * Every method is a straight function of its inputs.
 */
final class HtImFrameDecoder {

    private final ObjectMapper om;

    HtImFrameDecoder(ObjectMapper om) {
        this.om = om;
    }

    /** Decoded push body — shared by live F2 frames and replayed offline packets. */
    sealed interface F2Push {
        record Receipt(String msgId) implements F2Push {}
        record Poke() implements F2Push {}
        record Json(JsonNode root) implements F2Push {}
        record Unknown(int firstByte, byte[] bytes) implements F2Push {}
        record DecryptFailed() implements F2Push {}
        record Ignored() implements F2Push {}
    }

    /** An offline packet decoded from its base64 wrapper. */
    record OfflinePacket(Header header, F2Push body) {}

    Optional<JsonNode> decodeF1(byte[] data, int payloadLen) {
        byte[] raw = HtImPacketFramer.copyPayload(data, payloadLen);
        byte[] decompressed = HtImPacketFramer.inflate(raw);
        if (decompressed == null) return Optional.empty();

        String text = stripNulls(decompressed).trim();
        if (!text.startsWith("{")) return Optional.empty();

        try {
            return Optional.of(om.readTree(text));
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    F2Push decodeF2(byte[] data, int payloadLen, byte[] sessionKey) {
        if (sessionKey == null || payloadLen == 0) return new F2Push.Ignored();

        byte[] encPayload = HtImPacketFramer.copyPayload(data, payloadLen);
        byte[] decrypted;
        try {
            decrypted = QqTeaCipher.decrypt(encPayload, sessionKey);
        } catch (Exception _) {
            return new F2Push.DecryptFailed();
        }
        if (decrypted == null || decrypted.length == 0) return new F2Push.Ignored();

        return decodePushBody(decrypted);
    }

    private F2Push decodePushBody(byte[] decrypted) {
        int firstByte = decrypted[0] & 0xFF;

        if (firstByte == 0x25) {
            if (decrypted.length < 38) return new F2Push.Ignored();
            String msgId = stripNulls(Arrays.copyOfRange(decrypted, 2, 38)).trim();
            return new F2Push.Receipt(msgId);
        }
        if (firstByte == 0x08) return new F2Push.Poke();

        byte[] finalBytes;
        if (firstByte == 0x78) {
            finalBytes = HtImPacketFramer.inflate(decrypted);
            if (finalBytes == null) return new F2Push.Ignored();
        } else if (firstByte == 0x7B) {
            finalBytes = decrypted;
        } else {
            return new F2Push.Unknown(firstByte, decrypted);
        }

        String jsonStr = stripNulls(finalBytes).trim();
        if (!jsonStr.startsWith("{")) return new F2Push.Ignored();
        try {
            return new F2Push.Json(om.readTree(jsonStr));
        } catch (Exception _) {
            return new F2Push.Ignored();
        }
    }

    boolean decodeTypingStatus(byte[] data, int payloadLen, byte[] sessionKey, int keyType) {
        byte[] payload = HtImPacketFramer.copyPayload(data, payloadLen);
        if (keyType == 1 && sessionKey != null && payload.length > 0) {
            try {
                byte[] dec = QqTeaCipher.decrypt(payload, sessionKey);
                if (dec != null) payload = dec;
            } catch (Exception ignored) {
                // fall through with undecrypted payload
            }
        }
        if (payload.length > 0 && (payload[0] & 0xFF) == 0x78) {
            byte[] inflated = HtImPacketFramer.inflate(payload);
            if (inflated != null) payload = inflated;
        }
        if (payload.length >= 6) {
            return ((payload[4] & 0xFF) | ((payload[5] & 0xFF) << 8)) == 1;
        }
        return true; // reference client's default for an unparseably-short body
    }

    Optional<OfflinePacket> decodeOfflinePacket(String base64, byte[] sessionKey) {
        try {
            byte[] raw = Base64.getDecoder().decode(base64);
            if (raw.length < HEADER_LEN) return Optional.empty();

            ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            int keyType = buf.get(2) & 0xFF;
            int cmdId   = buf.getShort(4) & 0xFFFF;
            long fromId = buf.getInt(8) & 0xFFFFFFFFL;
            long toId   = buf.getInt(12) & 0xFFFFFFFFL;
            int bodyLen = Math.max(0, Math.min(buf.getInt(16), raw.length - HEADER_LEN));

            byte[] payload = new byte[bodyLen];
            System.arraycopy(raw, HEADER_LEN, payload, 0, bodyLen);
            if (payload.length == 0) return Optional.empty();

            if (keyType == 1 && sessionKey != null) {
                try {
                    byte[] dec = QqTeaCipher.decrypt(payload, sessionKey);
                    if (dec != null && dec.length > 0) payload = dec;
                } catch (Exception ignored) {}
            } else if (keyType == 0 && sessionKey != null && payload.length > 0 && !isRecognizedMagic(payload[0] & 0xFF)) {
                try {
                    byte[] dec = QqTeaCipher.decrypt(payload, sessionKey);
                    if (dec != null && dec.length > 0 && isRecognizedMagic(dec[0] & 0xFF)) payload = dec;
                } catch (Exception ignored) {}
            }

            if (payload.length >= 2 && (payload[0] & 0xFF) == 0x1F && (payload[1] & 0xFF) == 0x8B) {
                byte[] gunzipped = gunzip(payload);
                if (gunzipped != null) {
                    String jsonStr = stripNulls(gunzipped).trim();
                    if (jsonStr.startsWith("{") || jsonStr.startsWith("[")) {
                        try {
                            Header header = new Header(PKT_PUSH, keyType, cmdId, 0, fromId, toId, bodyLen);
                            return Optional.of(new OfflinePacket(header, new F2Push.Json(om.readTree(jsonStr))));
                        } catch (Exception ignored) {}
                    }
                }
                return Optional.empty();
            }

            Header header = new Header(PKT_PUSH, keyType, cmdId, 0, fromId, toId, bodyLen);
            return Optional.of(new OfflinePacket(header, decodePushBody(payload)));
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    /**
     * Parses the upstream MSG-ACK body the server emits as cmdId 16386 on a 0xF2 packet —
     * the receipt confirming it accepted an outbound DM we sent via {@code CMD_PRIVATE_MSG
     * = 16385}. Body layout (matches connectwebsock.js's {@code decodeCmd16386}):
     * <pre>
     *   [u16 strLen][strVal UTF-8][u64 LE sequence][prefix byte]
     * </pre>
     * where {@code strVal} is typically a UUID msgId, {@code prefix} is non-zero for a normal
     * ACK, and a ≤16-byte body indicates an empty/failure ACK. The body is normally raw
     * bytes — but historic captures have seen it zlib-compressed, so {@code inflate} first.
     *
     * <p>On any parse failure (and on the empty/failure-ACK path) returns a record with
     * empty msgId + sequence 0 so the caller can still emit an event and the UI can render
     * "✓ sent" vs "✓ delivered" with the prefix byte as the discriminator.
     */
    Optional<MessageAckView> decodeMsgAck(byte[] payload) {
        if (payload == null || payload.length == 0) return Optional.empty();

        // Try a zlib-inflate first — captures have shown MSG-ACK bodies sometimes
        // zlib-compressed (leading 0x78). Fall through to raw if not.
        byte[] raw = payload;
        if ((payload[0] & 0xFF) == 0x78) {
            byte[] inflated = HtImPacketFramer.inflate(payload);
            if (inflated != null) raw = inflated;
        }

        // Empty/failure ACK — short body (≤16 bytes by legacy convention).
        if (raw.length <= 16) {
            return Optional.of(new MessageAckView("", 0L, 0));
        }

        // Path 1: strict sequential layout, matching decodeCmd16386 exactly. lenField counts
        // the prefix byte itself (lenField = 1 + msgId.length), so the string runs [3, 2+lenField)
        // and the sequence starts at 2+lenField, NOT 3+lenField.
        try {
            java.nio.ByteBuffer beView = java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.BIG_ENDIAN);
            java.nio.ByteBuffer leView = java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int lenBE = beView.getShort(0) & 0xFFFF;
            int lenLE = leView.getShort(0) & 0xFFFF;

            int strLen = lenBE;
            if (2 + lenBE > raw.length && 2 + lenLE <= raw.length) {
                strLen = lenLE;
            }

            if (strLen > 0 && 2 + strLen <= raw.length) {
                int prefix = raw[2] & 0xFF;
                String strVal = stripNulls(Arrays.copyOfRange(raw, 3, 2 + strLen)).trim();
                long sequence = 0L;
                int nextOffset = 2 + strLen;
                if (nextOffset < raw.length && raw[nextOffset] == 0) nextOffset++;
                int sequenceBytes = raw.length - nextOffset;
                if (sequenceBytes >= 8) {
                    sequence = leView.getLong(nextOffset);
                } else if (sequenceBytes >= 4) {
                    sequence = leView.getInt(nextOffset) & 0xFFFFFFFFL;
                }
                if (UUID_PATTERN.matcher(strVal).find()) {
                    return Optional.of(new MessageAckView(strVal, sequence, prefix));
                }
            }
        } catch (Exception _) {
            // fall through to UUID regex path
        }

        // Path 2: regex UUID fallback. Scan the body for a UUID and grab the trailing
        // 8 bytes as sequence (legacy `decodeCmd16386` second fallback).
        try {
            String text = new String(raw, StandardCharsets.UTF_8);
            java.util.regex.Matcher m = UUID_PATTERN.matcher(text);
            if (m.find()) {
                String msgId = m.group();
                long sequence = 0L;
                if (raw.length >= 8) {
                    java.nio.ByteBuffer le = java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    sequence = (raw.length >= 12)
                        ? (le.getLong(raw.length - 8) & 0xFFFFFFFFFFFFFFFFL)
                        : (le.getInt(raw.length - 4) & 0xFFFFFFFFL);
                }
                return Optional.of(new MessageAckView(msgId, sequence, 0));
            }
        } catch (Exception _) {
            // fall through
        }
        return Optional.empty();
    }

    record MessageAckView(String msgId, long sequence, int prefix) {}

    private static final java.util.regex.Pattern UUID_PATTERN = java.util.regex.Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", java.util.regex.Pattern.CASE_INSENSITIVE);

    private static String stripNulls(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).replace("\0", "");
    }

    private static boolean isRecognizedMagic(int firstByte) {
        return firstByte == 0x78 || firstByte == 0x1F || firstByte == 0x7B || firstByte == 0x25;
    }

    private static byte[] gunzip(byte[] data) {
        try (var bais = new java.io.ByteArrayInputStream(data);
             var gzip = new java.util.zip.GZIPInputStream(bais);
             var baos = new java.io.ByteArrayOutputStream(data.length * 4)) {
            gzip.transferTo(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}