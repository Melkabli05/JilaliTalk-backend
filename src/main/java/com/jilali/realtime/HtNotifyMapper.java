package com.jilali.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Parses LiveHub's plain-JSON {@code /livehub/ws/conn} frames into {@link RoomRealtimeEvent}s.
 * Frame shapes (confirmed against the real backend):
 * <ul>
 *   <li>{@code {"heartbeat_sec": 60}} — server heartbeat config, not a room event</li>
 *   <li>{@code {"heartbeat_time": ...}} — heartbeat response, not a room event</li>
 *   <li>{@code {"event": {"notify_type": "N", "notify_info": {...}}, "msg_id": "..."}} — room event</li>
 * </ul>
 * Only {@code notify_type} codes with prior empirical confirmation get a typed mapping;
 * everything else falls through to {@link RoomRealtimeEvent.Raw} (see the design spec).
 */
@Singleton
public class HtNotifyMapper {

    private static final Logger log = LoggerFactory.getLogger(HtNotifyMapper.class);

    private final ObjectMapper om;

    public HtNotifyMapper(ObjectMapper om) {
        this.om = om;
    }

    public OptionalLong heartbeatSec(String text) {
        JsonNode root = readTreeOrNull(text);
        if (root != null && root.has("heartbeat_sec")) {
            return OptionalLong.of(root.get("heartbeat_sec").asLong(60));
        }
        return OptionalLong.empty();
    }

    public boolean isHeartbeatResponse(String text) {
        JsonNode root = readTreeOrNull(text);
        return root != null && root.has("heartbeat_time");
    }

    public Optional<String> msgId(String text) {
        JsonNode root = readTreeOrNull(text);
        if (root == null) return Optional.empty();
        JsonNode n = root.get("msg_id");
        return (n != null && !n.isNull()) ? Optional.of(n.asText()) : Optional.empty();
    }

    public Optional<RoomRealtimeEvent> map(String text) {
        JsonNode root = readTreeOrNull(text);
        if (root == null) {
            return Optional.of(new RoomRealtimeEvent.Error("Malformed LiveHub frame: " + abbrev(text)));
        }
        if (root.has("heartbeat_sec") || root.has("heartbeat_time")) {
            return Optional.empty();
        }

        JsonNode eventNode = root.get("event");
        if (eventNode == null || !eventNode.isObject()) {
            log.trace("LiveHub frame has no 'event' object — skipping: {}", abbrev(text));
            return Optional.empty();
        }

        String type = eventNode.has("notify_type") ? eventNode.get("notify_type").asText() : "";
        JsonNode info = eventNode.path("notify_info");

        try {
            RoomRealtimeEvent event = switch (type) {
                case "1" -> mapTypeOne(info);
                case "2" -> new RoomRealtimeEvent.UserQuit(textOr(info, "user_id", ""));
                case "4" -> {
                    if (info.has("lucky_bag_id")) {
                        yield new RoomRealtimeEvent.Raw(type, om.treeToValue(root, Object.class));
                    }
                    yield new RoomRealtimeEvent.StageJoin(om.treeToValue(info, RoomRealtimeEvent.StageUserEvent.class));
                }
                case "5" -> {
                    if (info.has("lucky_bag_id")) {
                        yield new RoomRealtimeEvent.Raw(type, om.treeToValue(root, Object.class));
                    }
                    if (info.has("coin")) {
                        yield null;
                    }
                    yield new RoomRealtimeEvent.StageQuit(textOr(info, "user_id", ""));
                }
                case "6" -> info.has("lucky_bag_id")
                    ? new RoomRealtimeEvent.Raw(type, om.treeToValue(root, Object.class))
                    : null;
                case "8" -> new RoomRealtimeEvent.MicOpened(textOr(info, "user_id", ""));
                case "9" -> new RoomRealtimeEvent.MicClosed(textOr(info, "user_id", ""));
                case "10" -> new RoomRealtimeEvent.StageRaiseHand(textOr(info, "user_id", ""), 1);
                case "11" -> new RoomRealtimeEvent.StageRaiseHand(textOr(info, "user_id", ""), 2);
                case "18" -> new RoomRealtimeEvent.StageInvite(textOr(info, "user_id", ""));
                case "23" -> new RoomRealtimeEvent.StageJoin(om.treeToValue(info, RoomRealtimeEvent.StageUserEvent.class));
                case "25" -> new RoomRealtimeEvent.Comment(mapComment(info));
                case "29" -> new RoomRealtimeEvent.StageKick(
                    textOr(info, "user_id", ""),
                    textOr(info, "manager_name", ""),
                    textOr(info, "cname", "")
                );
                case "30" -> new RoomRealtimeEvent.StageDeviceControl(textOr(info, "user_id", ""), 1, 1);
                case "34" -> new RoomRealtimeEvent.ModAccepted(textOr(info, "user_id", ""));
                case "35" -> new RoomRealtimeEvent.ModRemoved(textOr(info, "user_id", ""));
                case "40" -> new RoomRealtimeEvent.MicOpened(textOr(info, "user_id", "")); // mic on
                case "48" -> new RoomRealtimeEvent.ModInvite(textOr(info, "user_id", ""));
                case "53" -> new RoomRealtimeEvent.Follow(
                    textOr(info, "nickname", ""),
                    info.path("status").asInt(0)
                );
                case "3" -> {
                    if (info.has("lucky_bag_id")) {
                        yield new RoomRealtimeEvent.Raw(type, om.treeToValue(root, Object.class));
                    }
                    int gameType = info.path("game_type").asInt(-1);
                    int kickType = info.path("kick_type").asInt(-1);
                    if (gameType == 2) {
                        yield new RoomRealtimeEvent.WhiteboardActivated(textOr(info, "cname", ""));
                    }
                    if (gameType == 0) {
                        yield new RoomRealtimeEvent.WhiteboardDeactivated(textOr(info, "cname", ""));
                    }
                    if (kickType == 1) {
                        yield new RoomRealtimeEvent.RoomKick(
                            textOr(info, "user_id", ""),
                            textOr(info, "nickname", ""),
                            textOr(info, "manager_name", ""),
                            textOr(info, "cname", "")
                        );
                    }
                    yield new RoomRealtimeEvent.Raw(type, om.treeToValue(root, Object.class));
                }
                default -> new RoomRealtimeEvent.Raw(type, om.treeToValue(root, Object.class));
            };
            return Optional.ofNullable(event);
        } catch (Exception e) {
            log.warn("LiveHub event mapping failed for notify_type='{}': {}", type, e.getMessage());
            return Optional.of(new RoomRealtimeEvent.Error("Mapping failed for notify_type=" + type + ": " + e.getMessage()));
        }
    }

    private RoomRealtimeEvent mapTypeOne(JsonNode info) throws com.fasterxml.jackson.core.JsonProcessingException {
        JsonNode usersNode = info.path("users");
        if (info.path("type").asInt(-1) == 1 && usersNode.isArray() && !usersNode.isEmpty()) {
            List<RoomRealtimeEvent.GiftEvent> gifts = new ArrayList<>();
            for (JsonNode userNode : usersNode) {
                if (userNode.isObject()) {
                    gifts.add(om.treeToValue(userNode, RoomRealtimeEvent.GiftEvent.class));
                }
            }
            if (!gifts.isEmpty()) {
                return new RoomRealtimeEvent.Gift(gifts);
            }
        }
        String nickname = textOr(info, "nickname", textOr(info, "send_nickname", "Someone"));
        return new RoomRealtimeEvent.UserJoin(textOr(info, "user_id", ""), nickname);
    }

    private RoomRealtimeEvent.CommentEvent mapComment(JsonNode info) {
        JsonNode msg = info.path("msg");
        JsonNode textNode = msg.path("text");
        String text = textNode.isObject() ? textOr(textNode, "text", "") : textNode.asText("");
        RoomRealtimeEvent.ReplyInfoEvent replyInfo = null;
        JsonNode replyNode = msg.get("reply_info");
        if (replyNode != null && replyNode.isObject()) {
            replyInfo = new RoomRealtimeEvent.ReplyInfoEvent(
                textOr(replyNode, "msg_id", ""),
                replyNode.path("from_id").asLong(0),
                textOr(replyNode, "from_nickname", ""),
                textOr(replyNode, "text", ""),
                textOr(replyNode, "msg_type", "text"));
        }
        return new RoomRealtimeEvent.CommentEvent(
            textOr(msg, "msg_id", ""),
            textOr(info, "user_id", ""),
            textOr(info, "nickname", "Anonymous"),
            textOr(info, "head_url", ""),
            text,
            msg.has("send_ts") ? msg.get("send_ts").asLong() : System.currentTimeMillis(),
            replyInfo);
    }

    private JsonNode readTreeOrNull(String text) {
        try {
            return om.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : fallback;
    }

    private static String abbrev(String s) {
        return s != null && s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }
}
