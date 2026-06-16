package com.jilali.ht;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.ht.dto.HtEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Parses incoming text frames from HelloTalk's LiveHub WebSocket into typed {@link HtEvent}s.
 *
 * <p>LiveHub sends <b>plain-JSON text frames</b>. Each frame is one of:
 * <ul>
 *   <li>{@code {"heartbeat_sec": 60}} — server heartbeat config (not a room event)</li>
 *   <li>{@code {"heartbeat_time": 1234}} — heartbeat response (not a room event)</li>
 *   <li>{@code {"event": {"notify_type":"N","notify_info":{…}}, "msg_id":"…"}} — room event</li>
 * </ul>
 *
 * <p>{@code notify_type} values observed in the old frontend ({@code fireRoomWebSocket}):
 * <ul>
 *   <li>"1"  — user join (or gift if {@code notify_info.type=1} with {@code users} array)</li>
 *   <li>"2"  — user leave</li>
 *   <li>"4"  — stage join, or goodie-bag announce (if {@code lucky_bag_id} present)</li>
 *   <li>"5"  — stage leave, or goodie-bag result</li>
 *   <li>"10" — raise hand</li>
 *   <li>"11" — lower hand</li>
 *   <li>"18" — stage invite</li>
 *   <li>"23" — full stage refresh</li>
 *   <li>"25" — new comment</li>
 *   <li>"30" — mic muted</li>
 *   <li>"48" — mod invite</li>
 * </ul>
 *
 * <p>Thread-safe: stateless after construction.
 */
@Singleton
public class HtEventParser {

    private static final Logger log = LoggerFactory.getLogger(HtEventParser.class);

    private final ObjectMapper om;

    public HtEventParser(ObjectMapper om) {
        this.om = om;
    }

    /**
     * Returns the server-provided heartbeat interval in seconds from a config frame,
     * or empty if this frame is not the heartbeat-config frame.
     */
    public OptionalLong heartbeatSec(String text) {
        try {
            var root = om.readTree(text);
            if (root.has("heartbeat_sec")) {
                return OptionalLong.of(root.get("heartbeat_sec").asLong(60));
            }
        } catch (Exception ignored) {}
        return OptionalLong.empty();
    }

    /**
     * Returns {@code true} if this frame is a heartbeat-response frame
     * ({@code heartbeat_time} field). The caller should reschedule the next heartbeat.
     */
    public boolean isHeartbeatResponse(String text) {
        try {
            return om.readTree(text).has("heartbeat_time");
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Extracts the {@code msg_id} from an event frame for ACKing, or {@code null} if absent.
     */
    public String msgId(String text) {
        try {
            var root = om.readTree(text);
            var n = root.get("msg_id");
            return (n != null && !n.isNull()) ? n.asText() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Parses a LiveHub text frame into a typed {@link HtEvent}.
     *
     * @param text raw text frame from LiveHub
     * @return an {@link HtEvent} for room events; empty for heartbeat frames
     */
    public Optional<HtEvent> parse(String text) {
        try {
            var root = om.readTree(text);
            if (root.has("heartbeat_sec") || root.has("heartbeat_time")) {
                return Optional.empty();
            }

            var eventNode = root.get("event");
            if (eventNode == null || !eventNode.isObject()) {
                log.trace("LiveHub frame has no 'event' object — skipping: {}", abbrev(text));
                return Optional.empty();
            }

            String type = eventNode.has("notify_type") ? eventNode.get("notify_type").asText() : "";
            var info = eventNode.path("notify_info");

            String uid = textOr(info, "user_id", "");
            HtEvent event = switch (type) {
                case "25"       -> parseComment(info);
                case "1"        -> parseUserJoin(info);
                case "2"        -> new HtEvent.UserLeave(uid);
                case "4", "5", "23" -> new HtEvent.StageUpdate(toObject(info));
                case "10"       -> new HtEvent.HandRaise(uid);
                case "11"       -> new HtEvent.HandLower(uid);
                case "18"       -> new HtEvent.StageInvite(uid);
                case "30"       -> new HtEvent.MicMuted(uid);
                case "48"       -> new HtEvent.ModInvite(uid);
                default -> {
                    log.trace("LiveHub unknown notify_type='{}' — passing through as raw", type);
                    yield new HtEvent.Raw(type, toObject(root));
                }
            };
            return Optional.of(event);

        } catch (Exception e) {
            log.warn("LiveHub event parse error: {}", e.getMessage());
            return Optional.of(new HtEvent.Error("Parse error: " + e.getMessage()));
        }
    }

    // ---- notify_type parsers -------------------------------------------------

    private HtEvent.Comment parseComment(JsonNode info) {
        var msg = info.path("msg");
        var textNode = msg.path("text");
        return new HtEvent.Comment(
            textOr(msg, "msg_id", ""),
            textOr(info, "user_id", ""),
            textOr(info, "nickname", "Anonymous"),
            textOr(info, "head_url", ""),
            textNode.isObject() ? textOr(textNode, "text", "") : textNode.asText(""),
            msg.has("send_ts") ? msg.get("send_ts").asLong() : System.currentTimeMillis()
        );
    }

    private HtEvent.UserJoin parseUserJoin(JsonNode info) {
        return new HtEvent.UserJoin(
            textOr(info, "user_id", ""),
            textOr(info, "nickname", textOr(info, "send_nickname", "Someone"))
        );
    }

    // ---- Jackson helpers ----------------------------------------------------

    private static String textOr(JsonNode node, String field, String fallback) {
        var n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : fallback;
    }

    private Object toObject(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try {
            return om.treeToValue(node, Object.class);
        } catch (Exception e) {
            return node.toString();
        }
    }

    private static String abbrev(String s) {
        return s != null && s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }
}
