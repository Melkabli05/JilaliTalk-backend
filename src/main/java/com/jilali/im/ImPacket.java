package com.jilali.im;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * {@code ht_im/sock}'s 20-byte packet header + payload framing. Ported from the reference
 * client's {@code buildPacket}/{@code SendAck}/message-handler header reads
 * (connectwebsock.js) — byte-for-byte the same layout:
 *
 * <pre>
 * offset  size  field
 * 0       1     packet type (0xF0 outgoing login/heartbeat, 0xF1 login/pong response,
 *                            0xF2 push notification, 0xF3 ack, 0xF5 typing)
 * 1-3     3     fixed marker bytes: 0x04 0x00 0x01
 * 4-5     2     command id (uint16 LE)
 * 6-7     2     sequence (uint16 LE)
 * 8-11    4     from/uid (uint32 LE)
 * 12-15   4     to id (uint32 LE)
 * 16-19   4     payload length (uint32 LE)
 * 20+     n     payload
 * </pre>
 */
public final class ImPacket {

    public static final int HEADER_LEN = 20;

    public static final int CMD_LOGIN = 0x1025;
    public static final int CMD_HEARTBEAT = 0x9001;
    public static final int CMD_PONG = 0x9002;

    public static final int TYPE_OUTGOING = 0xF0;
    public static final int TYPE_LOGIN_OR_PONG = 0xF1;
    public static final int TYPE_PUSH_NOTIFY = 0xF2;
    public static final int TYPE_ACK = 0xF3;
    public static final int TYPE_TYPING = 0xF5;

    private ImPacket() {}

    public record Header(int packetType, int cmdId, int sequence, long fromId, long toId, int payloadLen) {}

    public static Header parseHeader(byte[] raw) {
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int packetType = raw[0] & 0xFF;
        int cmdId = buf.getShort(4) & 0xFFFF;
        int seq = buf.getShort(6) & 0xFFFF;
        long fromId = buf.getInt(8) & 0xFFFFFFFFL;
        long toId = buf.getInt(12) & 0xFFFFFFFFL;
        int payloadLen = buf.getInt(16);
        return new Header(packetType, cmdId, seq, fromId, toId, payloadLen);
    }

    public static byte[] payloadOf(byte[] raw, Header header) {
        int len = Math.min(header.payloadLen(), raw.length - HEADER_LEN);
        if (len <= 0) return new byte[0];
        byte[] payload = new byte[len];
        System.arraycopy(raw, HEADER_LEN, payload, 0, len);
        return payload;
    }

    /** Builds an outgoing packet (login, heartbeat) — packet type fixed at {@link #TYPE_OUTGOING}. */
    public static byte[] build(long uid, int commandId, int seq, byte[] payload) {
        byte[] buf = new byte[HEADER_LEN + payload.length];
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        buf[0] = (byte) TYPE_OUTGOING;
        buf[1] = 0x04;
        buf[2] = 0x00;
        buf[3] = 0x01;
        bb.putShort(4, (short) commandId);
        bb.putShort(6, (short) seq);
        bb.putInt(8, (int) uid);
        bb.putInt(12, 0);
        bb.putInt(16, payload.length);
        System.arraycopy(payload, 0, buf, HEADER_LEN, payload.length);
        return buf;
    }

    /** Builds an ACK for an inbound push-notification packet — copies the incoming sequence
     *  and the from/to id pair verbatim, bumps the cmdId by one (matching the reference
     *  client's default), zero-length payload. */
    public static byte[] buildAck(byte[] incoming, Header incomingHeader) {
        byte[] buf = new byte[HEADER_LEN];
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        buf[0] = (byte) TYPE_ACK;
        buf[1] = 0x04;
        buf[2] = 0x00;
        buf[3] = 0x01;
        bb.putShort(4, (short) (incomingHeader.cmdId() + 1));
        bb.putShort(6, (short) incomingHeader.sequence());
        System.arraycopy(incoming, 8, buf, 8, 8); // from id + to id, copied verbatim
        bb.putInt(16, 0);
        return buf;
    }
}
