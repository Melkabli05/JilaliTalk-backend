# Task 3: `HtImFrameDecoder` — pure byte-level decoding

## Context
Tasks 1-2 are done (shared utilities, HtImPacketFramer updated). Task 3 creates `HtImFrameDecoder` — a pure, stateless class handling all byte-level decode logic from the current `HtImUpstreamConnector`. No networking, no mutable state.

## Dependencies
- `HtImPacketFramer.inflate(byte[])` (just added in Task 2)
- `HtImPacketFramer.copyPayload(byte[], int)` (just added in Task 2)
- `QqTeaCipher.decrypt` (existing, `com.jilali.crypto`)
- Jackson `ObjectMapper`/`JsonNode` (existing)

## Files to create
- `src/main/java/com/jilali/im/HtImFrameDecoder.java`
- `src/test/java/com/jilali/im/HtImFrameDecoderTest.java`

## Exact code to implement

### HtImFrameDecoder.java
```java
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
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    F2Push decodeF2(byte[] data, int payloadLen, byte[] sessionKey) {
        if (sessionKey == null || payloadLen == 0) return new F2Push.Ignored();

        byte[] encPayload = HtImPacketFramer.copyPayload(data, payloadLen);
        byte[] decrypted;
        try {
            decrypted = QqTeaCipher.decrypt(encPayload, sessionKey);
        } catch (Exception e) {
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
        } catch (Exception e) {
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
        return payload.length >= 6 && ((payload[4] & 0xFF) | ((payload[5] & 0xFF) << 8)) == 1;
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
            }

            Header header = new Header(PKT_PUSH, keyType, cmdId, 0, fromId, toId, bodyLen);
            return Optional.of(new OfflinePacket(header, decodePushBody(payload)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String stripNulls(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).replace("\0", "");
    }
}
```

### HtImFrameDecoderTest.java — write FIRST, verify FAIL, then write class, verify PASS

```java
package com.jilali.im;

import static com.jilali.im.HtImPacketFramer.HEADER_LEN;
import static com.jilali.im.HtImPacketFramer.buildPacket;
import static com.jilali.im.HtImPacketFramer.deflate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.crypto.QqTeaCipher;
import com.jilali.im.HtImFrameDecoder.F2Push;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

class HtImFrameDecoderTest {

    private final ObjectMapper om = new ObjectMapper();
    private final HtImFrameDecoder decoder = new HtImFrameDecoder(om);
    private static final byte[] SESSION_KEY = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void decodeF1ParsesDeflatedJson() {
        byte[] json = "{\"status\":0,\"data\":{}}".getBytes(StandardCharsets.UTF_8);
        byte[] packet = buildPacket(1L, 0, deflate(json));

        Optional<JsonNode> result = decoder.decodeF1(packet, packet.length - HEADER_LEN);

        assertTrue(result.isPresent());
        assertEquals(0, result.get().path("status").asInt());
    }

    @Test
    void decodeF1IgnoresNonJsonPayload() {
        byte[] packet = buildPacket(1L, 0, deflate("not json".getBytes(StandardCharsets.UTF_8)));

        Optional<JsonNode> result = decoder.decodeF1(packet, packet.length - HEADER_LEN);

        assertTrue(result.isEmpty());
    }

    @Test
    void decodeF2DecryptsInflatesAndParsesJson() {
        byte[] json = "{\"msg_type\":\"text\",\"from_id\":5}".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = QqTeaCipher.encrypt(deflate(json), SESSION_KEY);
        byte[] packet = buildPacket(1L, 0, encrypted);

        F2Push result = decoder.decodeF2(packet, packet.length - HEADER_LEN, SESSION_KEY);

        var jsonPush = (F2Push.Json) result;
        assertEquals("text", jsonPush.root().path("msg_type").asText());
    }

    @Test
    void decodeF2WithoutSessionKeyIsIgnored() {
        byte[] packet = buildPacket(1L, 0, "irrelevant".getBytes(StandardCharsets.UTF_8));

        F2Push result = decoder.decodeF2(packet, packet.length - HEADER_LEN, null);

        assertEquals(new F2Push.Ignored(), result);
    }

    @Test
    void decodeF2RecognizesReadReceipt() {
        byte[] plain = new byte[40];
        plain[0] = 0x25;
        byte[] msgIdBytes = "abc-123".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(msgIdBytes, 0, plain, 2, msgIdBytes.length);
        byte[] encrypted = QqTeaCipher.encrypt(plain, SESSION_KEY);
        byte[] packet = buildPacket(1L, 0, encrypted);

        F2Push result = decoder.decodeF2(packet, packet.length - HEADER_LEN, SESSION_KEY);

        var receipt = (F2Push.Receipt) result;
        assertEquals("abc-123", receipt.msgId());
    }

    @Test
    void decodeF2RecognizesPoke() {
        byte[] plain = new byte[]{(byte) 0x08};
        byte[] encrypted = QqTeaCipher.encrypt(plain, SESSION_KEY);
        byte[] packet = buildPacket(1L, 0, encrypted);

        F2Push result = decoder.decodeF2(packet, packet.length - HEADER_LEN, SESSION_KEY);

        assertEquals(new F2Push.Poke(), result);
    }

    @Test
    void decodeF2RecognizesUnknownFirstByte() {
        byte[] plain = new byte[]{(byte) 0xAB, 1, 2, 3};
        byte[] encrypted = QqTeaCipher.encrypt(plain, SESSION_KEY);
        byte[] packet = buildPacket(1L, 0, encrypted);

        F2Push result = decoder.decodeF2(packet, packet.length - HEADER_LEN, SESSION_KEY);

        var unknown = (F2Push.Unknown) result;
        assertEquals(0xAB, unknown.firstByte());
    }

    @Test
    void decodeTypingStatusReadsTypingBit() {
        byte[] payload = new byte[]{0, 0, 0, 0, 1, 0};

        boolean typing = decoder.decodeTypingStatus(buildPacket(1L, 0, payload), payload.length, null, 0);

        assertTrue(typing);
    }

    @Test
    void decodeTypingStatusReadsStoppedBit() {
        byte[] payload = new byte[]{0, 0, 0, 0, 0, 0};

        boolean typing = decoder.decodeTypingStatus(buildPacket(1L, 0, payload), payload.length, null, 0);

        assertFalse(typing);
    }

    @Test
    void decodeOfflinePacketDecryptsAndParsesBase64Frame() {
        byte[] json = "{\"msg_type\":\"text\",\"from_id\":5}".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = QqTeaCipher.encrypt(json, SESSION_KEY);
        byte[] raw = new byte[HEADER_LEN + encrypted.length];
        raw[2] = 1;
        System.arraycopy(encrypted, 0, raw, HEADER_LEN, encrypted.length);
        java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(16, encrypted.length);
        String base64 = Base64.getEncoder().encodeToString(raw);

        Optional<HtImFrameDecoder.OfflinePacket> result = decoder.decodeOfflinePacket(base64, SESSION_KEY);

        assertTrue(result.isPresent());
        var jsonPush = (F2Push.Json) result.get().body();
        assertEquals("text", jsonPush.root().path("msg_type").asText());
    }

    @Test
    void decodeOfflinePacketReturnsEmptyForTooShortInput() {
        String base64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});

        assertTrue(decoder.decodeOfflinePacket(base64, SESSION_KEY).isEmpty());
    }
}
```

## Test contract
Write test first (should fail — class doesn't exist). Write class (should pass). Run full suite: `./gradlew test`. All 10 new tests must pass + all existing tests must remain green.

## Report file
`/home/mohammed/Desktop/JilaliTalk/jilalibff/.superpowers/sdd/task-3-report.md`

## Commit message
`feat(im): add HtImFrameDecoder, pure byte-level decoding for ht_im/sock frames`

## Working directory
`/home/mohammed/Desktop/JilaliTalk/jilalibff`
