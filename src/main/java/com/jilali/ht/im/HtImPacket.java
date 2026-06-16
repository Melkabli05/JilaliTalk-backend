package com.jilali.ht.im;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HelloTalk IM binary packet — 20-byte little-endian header + payload.
 *
 * <p>Header layout (from {@code buildPacket} + {@code prvgmsgpacket.js}):
 * <pre>
 * [0]     flag      – packet type (0xF0=out, 0xF1=resp, 0xF2=push, 0xF3=ack, 0xF5=typing)
 * [1]     version   – protocol version (4)
 * [2]     keyType   – 0=plaintext, 1=QQTEA encrypted
 * [3]     termType  – terminal type (1=Android)
 * [4-5]   cmdId     – command ID (uint16 LE)
 * [6-7]   seq       – sequence number (uint16 LE)
 * [8-11]  fromId    – sender user ID (uint32 LE)
 * [12-15] toId      – target user ID (uint32 LE)
 * [16-19] bodyLen   – payload length (uint32 LE)
 * [20+]   payload   – body bytes
 * </pre>
 *
 * <p>All fields are unsigned in the wire format; the Java record stores them as signed
 * ints/longs. Cast to {@code int &amp; 0xFFFFFFFFL} when sending.
 */
public record HtImPacket(
    int flag,
    int version,
    int keyType,
    int termType,
    int cmdId,
    int seq,
    long fromId,
    long toId,
    byte[] payload
) {
    public static final int HEADER_LEN = 20;

    // Seq counter: range 16000–99000 (matches the JS nextSeq() function)
    private static final AtomicInteger SEQ = new AtomicInteger(
        16000 + (int) (Math.random() * 83000)
    );

    private static int nextSeq() {
        return SEQ.updateAndGet(s -> s >= 99000 ? 16000 : s + 1);
    }

    // ---- Parsing ------------------------------------------------------------

    /** Parses a raw binary frame. Returns {@code null} if the frame is shorter than 20 bytes. */
    public static HtImPacket parse(byte[] raw) {
        if (raw == null || raw.length < HEADER_LEN) return null;
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int flag     = buf.get() & 0xFF;
        int version  = buf.get() & 0xFF;
        int keyType  = buf.get() & 0xFF;
        int termType = buf.get() & 0xFF;
        int cmdId    = buf.getShort() & 0xFFFF;
        int seq      = buf.getShort() & 0xFFFF;
        long fromId  = buf.getInt() & 0xFFFFFFFFL;
        long toId    = buf.getInt() & 0xFFFFFFFFL;
        int bodyLen  = (int)(buf.getInt() & 0xFFFFFFFFL);

        byte[] payload = new byte[Math.min(bodyLen, raw.length - HEADER_LEN)];
        buf.get(payload);
        return new HtImPacket(flag, version, keyType, termType, cmdId, seq, fromId, toId, payload);
    }

    // ---- Builders -----------------------------------------------------------

    /**
     * Builds a login packet (cmdId {@code 0x1025}) from the given JSON payload.
     *
     * <p>Corresponds to {@code buildPacket(userId, 0x1025, payloadBytes)} in JS.
     */
    public static byte[] buildLogin(long uid, byte[] jsonPayload) {
        return buildPacket(0xF0, 4, 0, 1, 0x1025, nextSeq(), uid, 0, jsonPayload);
    }

    /**
     * Builds a heartbeat packet (cmdId {@code 0x9001}).
     *
     * <p>Payload: userId (uint32 LE, 4 bytes) + current time millis (uint64 LE, 8 bytes).
     */
    public static byte[] buildHeartbeat(long uid) {
        ByteBuffer body = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        body.putInt((int)(uid & 0xFFFFFFFFL));
        body.putLong(System.currentTimeMillis());
        return buildPacket(0xF0, 4, 0, 1, 0x9001, nextSeq(), uid, 0, body.array());
    }

    /**
     * Builds an ACK for an inbound packet.
     *
     * <p>ACK flag is {@code 0xF3}; cmdId is {@code rawCmdId + 1}; seq and
     * fromId/toId are copied from the incoming header; payload is empty.
     *
     * <p>Corresponds to {@code SendAck(ws, incomingBuffer)} in JS.
     */
    public static byte[] buildAck(byte[] incoming) {
        if (incoming == null || incoming.length < HEADER_LEN) return null;
        ByteBuffer in = ByteBuffer.wrap(incoming).order(ByteOrder.LITTLE_ENDIAN);
        in.position(4);
        int rawCmdId = in.getShort() & 0xFFFF;
        int seq      = in.getShort() & 0xFFFF;
        long fromId  = in.getInt()   & 0xFFFFFFFFL;
        long toId    = in.getInt()   & 0xFFFFFFFFL;
        return buildPacket(0xF3, 4, 0, 1, (rawCmdId + 1) & 0xFFFF, seq, fromId, toId, new byte[0]);
    }

    /**
     * Builds an offline-message sync trigger (cmdId {@code 29967} or {@code 16453}).
     *
     * <p>Corresponds to {@code PrvMsgPacket.sendOfflineMessageTrigger(ws, fromId, lastId, cmdId)} in JS.
     */
    public static byte[] buildOfflineSyncTrigger(long uid, long lastId, int cmdId) {
        String json = "{\"last_id\":%d}".formatted(lastId);
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        return buildPacket(0xF0, 4, 0, 1, cmdId, nextSeq(), uid, 0, payload);
    }

    // ---- Low-level builder --------------------------------------------------

    private static byte[] buildPacket(
            int flag, int version, int keyType, int termType,
            int cmdId, int seq, long fromId, long toId,
            byte[] payload) {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_LEN + payload.length)
            .order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) flag);
        buf.put((byte) version);
        buf.put((byte) keyType);
        buf.put((byte) termType);
        buf.putShort((short) cmdId);
        buf.putShort((short) seq);
        buf.putInt((int)(fromId & 0xFFFFFFFFL));
        buf.putInt((int)(toId   & 0xFFFFFFFFL));
        buf.putInt(payload.length);
        buf.put(payload);
        return buf.array();
    }
}
