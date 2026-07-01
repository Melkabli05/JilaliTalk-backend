package com.jilali.im;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

/** Static helpers for building and parsing the 20-byte-header binary packets used by HelloTalk's ht_im/sock. */
final class HtImPacketFramer {

    static final int HEADER_LEN = 20;

    // Inbound packet types (byte 0)
    static final int PKT_RESPONSE = 0xF1; // login response / pong
    static final int PKT_PUSH     = 0xF2; // encrypted push notification
    static final int PKT_TYPING   = 0xF5; // typing indicator

    // Command IDs
    static final int CMD_LOGIN            = 0x1025; // 4133  — send credentials
    static final int CMD_HEARTBEAT        = 0x9001; // 36865 — ping
    static final int CMD_PONG             = 0x9002; // 36866 — server heartbeat response
    static final int CMD_TYPING           = 16407;  // 0x4017 — typing push
    static final int CMD_OFFLINE_SYNC      = 29967; // 0x750F — request offline DMs (group msgs)
    static final int CMD_OFFLINE_SYNC_PAGE = 16453; // 0x4015 — paginate / request DM offline msgs
    static final int CMD_OFFLINE_RESPONSE  = 16454; // 0x4016 — offline DM response
    static final int CMD_GROUP_RESPONSE    = 29968; // 0x7510 — group/room message sync response

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    private HtImPacketFramer() {}

    static int nextSeq() {
        return SEQ.getAndIncrement() & 0xFFFF;
    }

    /** Wrap a payload into a 20-byte-header binary packet for sending to the upstream WS. */
    static byte[] buildPacket(long userId, int commandId, byte[] payload) {
        return buildPacket(userId, commandId, 0xF0, payload);
    }

    /** Same as {@link #buildPacket(long, int, byte[])} but with a custom flag byte (e.g. 0xF2). */
    static byte[] buildPacket(long userId, int commandId, int flag, byte[] payload) {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_LEN + payload.length)
            .order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) (flag & 0xFF));
        buf.put((byte) 0x04);
        buf.put((byte) 0x00);
        buf.put((byte) 0x01);
        buf.putShort((short) commandId);
        buf.putShort((short) nextSeq());
        buf.putInt((int) userId);
        buf.putInt(0);             // toId
        buf.putInt(payload.length);
        buf.put(payload);
        return buf.array();
    }

    /**
     * Build a 20-byte ACK packet in response to an inbound push (0xF2) packet.
     * cmdId = inbound.cmdId + 1, seqNum and uid/toId bytes copied from inbound header.
     */
    static byte[] buildAck(byte[] inbound) {
        ByteBuffer in  = ByteBuffer.wrap(inbound).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer ack = ByteBuffer.allocate(HEADER_LEN).order(ByteOrder.LITTLE_ENDIAN);
        ack.put((byte) 0xF3);
        ack.put((byte) 0x04);
        ack.put((byte) 0x00);
        ack.put((byte) 0x01);
        int rawCmdId = in.getShort(4) & 0xFFFF;
        ack.putShort((short) (rawCmdId + 1));
        ack.putShort(in.getShort(6));   // same seqNum
        // copy 8 bytes at offset 8 (fromId + toId) verbatim
        ack.put(inbound, 8, 8);
        ack.putInt(0); // payloadLen = 0
        return ack.array();
    }

    /** Build the 30-second heartbeat packet (12-byte body: uid LE uint32 + timestamp LE uint64). */
    static byte[] buildHeartbeat(long userId) {
        ByteBuffer body = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        body.putInt((int) userId);
        body.putLong(System.currentTimeMillis());
        return buildPacket(userId, CMD_HEARTBEAT, body.array());
    }

    /** Zlib-deflate a byte array (produces data starting with 0x78). */
    static byte[] deflate(byte[] data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length + 16);
             DeflaterOutputStream dos = new DeflaterOutputStream(baos)) {
            dos.write(data);
            dos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("deflate failed", e);
        }
    }

    /** Copy the payload region (after the 20-byte header) out of a raw inbound packet. */
    static byte[] copyPayload(byte[] data, int payloadLen) {
        byte[] payload = new byte[payloadLen];
        System.arraycopy(data, HEADER_LEN, payload, 0, payloadLen);
        return payload;
    }

    /** Zlib-inflate a byte array; if it isn't zlib-compressed (doesn't start with 0x78),
     *  returns it unchanged. Tries both wrapped and raw-deflate modes since HelloTalk's
     *  server has been observed sending both. */
    static byte[] inflate(byte[] data) {
        if (data == null || data.length == 0) return data;
        if ((data[0] & 0xFF) != 0x78) return data; // not zlib compressed

        for (boolean nowrap : new boolean[]{false, true}) {
            try {
                Inflater inf = new Inflater(nowrap);
                inf.setInput(data);
                byte[] out = new byte[data.length * 8];
                int n = inf.inflate(out);
                inf.end();
                return Arrays.copyOf(out, n);
            } catch (Exception ignored) {
                // try the other mode
            }
        }
        return null;
    }

    /** Parse the 20-byte header from an inbound binary packet. Returns null if data is too short. */
    static Header parseHeader(byte[] data) {
        if (data == null || data.length < HEADER_LEN) return null;
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int packetType = buf.get(0) & 0xFF;
        int keyType    = buf.get(2) & 0xFF;
        int cmdId      = buf.getShort(4) & 0xFFFF;
        int seqNum     = buf.getShort(6) & 0xFFFF;
        long fromId    = buf.getInt(8) & 0xFFFFFFFFL;
        long toId      = buf.getInt(12) & 0xFFFFFFFFL;
        int payloadLen = buf.getInt(16);
        return new Header(packetType, keyType, cmdId, seqNum, fromId, toId, payloadLen);
    }

    record Header(int packetType, int keyType, int cmdId, int seqNum, long fromId, long toId, int payloadLen) {}
}
