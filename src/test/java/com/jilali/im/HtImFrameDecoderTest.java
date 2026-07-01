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