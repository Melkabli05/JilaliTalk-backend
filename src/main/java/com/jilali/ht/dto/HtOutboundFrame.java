package com.jilali.ht.dto;

/**
 * Outbound frames for HelloTalk's LiveHub WebSocket protocol.
 *
 * <p>LiveHub uses <b>plain-JSON text frames</b> — no binary encoding, no length prefix.
 * The {@code action} field distinguishes frame types:
 * <ul>
 *   <li>{@code 1} – {@link Init}: sent immediately after connection opens</li>
 *   <li>{@code 2} – {@link Heartbeat}: sent per server-configured {@code heartbeat_sec}</li>
 *   <li>{@code 3} – {@link Ack}: sent for every inbound event that carries a {@code msg_id}</li>
 * </ul>
 *
 * <p>Room actions (comment, raise-hand, etc.) are sent via the BFF REST API,
 * not through the LiveHub WebSocket.
 */
public sealed interface HtOutboundFrame permits
    HtOutboundFrame.Init,
    HtOutboundFrame.Heartbeat,
    HtOutboundFrame.Ack {

    /** Serializes this frame to a plain-JSON string for a WebSocket text frame. */
    String toJson();

    /**
     * Sent immediately after the WebSocket connection opens.
     * Registers this observer for room events.
     *
     * <p>Wire: {@code {"user_id":"…","cname":"…","action":1}}
     */
    record Init(String userId, String cname) implements HtOutboundFrame {
        @Override
        public String toJson() {
            return "{\"user_id\":\"%s\",\"cname\":\"%s\",\"action\":1}".formatted(userId, cname);
        }
    }

    /**
     * Periodic keepalive. The server provides the interval in its first response
     * ({@code heartbeat_sec}). Send this 5 seconds before that interval expires.
     *
     * <p>Wire: {@code {"cname":"…","user_id":"…","action":2,"is_visitor":true}}
     */
    record Heartbeat(String cname, String userId, boolean isVisitor) implements HtOutboundFrame {
        @Override
        public String toJson() {
            return "{\"cname\":\"%s\",\"user_id\":\"%s\",\"action\":2,\"is_visitor\":%b}"
                .formatted(cname, userId, isVisitor);
        }
    }

    /**
     * ACK for every inbound event that carries a {@code msg_id}.
     * HelloTalk stops delivering events if ACKs are not sent.
     *
     * <p>Wire: {@code {"msg_id":"…","action":3,"user_id":"…","cname":"…","is_visitor":true}}
     */
    record Ack(String msgId, String userId, String cname, boolean isVisitor) implements HtOutboundFrame {
        @Override
        public String toJson() {
            return "{\"msg_id\":\"%s\",\"action\":3,\"user_id\":\"%s\",\"cname\":\"%s\",\"is_visitor\":%b}"
                .formatted(msgId, userId, cname, isVisitor);
        }
    }
}
