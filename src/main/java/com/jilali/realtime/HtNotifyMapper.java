package com.jilali.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/** Parses LiveHub plain-JSON frames into {@link RoomRealtimeEvent}s. */
@Singleton
public class HtNotifyMapper {

    private static final Logger log = LoggerFactory.getLogger(HtNotifyMapper.class);

    private static final Set<String> TYPES_REQUIRING_USER_ID = Set.of(
        "2", "8", "9", "10", "11", "18", "23",
        "29", "30", "34", "35", "40", "48"
    );

    private static final String GIFT_TYPE = "1";

    private final ObjectMapper om;

    public HtNotifyMapper(ObjectMapper om) {
        this.om = om;
    }

    public OptionalLong heartbeatSec(String text) {
        return Optional.ofNullable(readTreeOrNull(text))
            .filter(root -> root.has("heartbeat_sec"))
            .map(root -> root.get("heartbeat_sec").asLong(60))
            .map(OptionalLong::of)
            .orElse(OptionalLong.empty());
    }

    public boolean isHeartbeatResponse(String text) {
        return Optional.ofNullable(readTreeOrNull(text))
            .map(root -> root.has("heartbeat_time"))
            .orElse(false);
    }

    public Optional<String> msgId(String text) {
        return Optional.ofNullable(readTreeOrNull(text))
            .map(root -> root.get("msg_id"))
            .filter(n -> !n.isNull())
            .map(JsonNode::asText);
    }

    public Optional<RoomRealtimeEvent> map(String text) {
        JsonNode root = readTreeOrNull(text);
        if (root == null) {
            String snippet = text != null && text.length() > 120 ? text.substring(0, 120) + "…" : text;
            return Optional.of(new RoomRealtimeEvent.Error("Malformed LiveHub frame: " + snippet));
        }
        if (root.has("heartbeat_sec") || root.has("heartbeat_time")) {
            return Optional.empty();
        }

        JsonNode eventNode = root.get("event");
        if (eventNode == null || !eventNode.isObject()) {
            log.trace("LiveHub frame has no 'event' object — skipping");
            return Optional.empty();
        }

        FrameContext ctx = new FrameContext(eventNode.path("notify_info"), eventNode.get("notify_type").asText(""), root);

        if (requiresUserId(ctx)) {
            log.debug("LiveHub frame dropped: notify_type='{}' has user_id=0", ctx.type());
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(mapEvent(ctx));
        } catch (Throwable _) {
            return Optional.of(new RoomRealtimeEvent.Error("Mapping failed for notify_type=" + ctx.type()));
        }
    }

    private RoomRealtimeEvent mapEvent(FrameContext ctx) throws Exception {
        return switch (ctx.type()) {
            case GIFT_TYPE -> mapTypeOne(ctx.info());
            case "2" -> new RoomRealtimeEvent.UserQuit(userId(ctx.info()));
            case "4" -> {
                RoomRealtimeEvent e = luckyBagOrNull(ctx);
                yield e != null ? e : new RoomRealtimeEvent.StageJoin(mapStageUser(ctx.info()));
            }
            case "5" -> {
                RoomRealtimeEvent e = luckyBagOrNull(ctx);
                yield e != null ? e : (ctx.info().has("coin") ? null : new RoomRealtimeEvent.StageQuit(userId(ctx.info())));
            }
            case "6" -> luckyBagOrNull(ctx);
            case "8" -> new RoomRealtimeEvent.MicOpened(userId(ctx.info()));
            case "9" -> new RoomRealtimeEvent.MicClosed(userId(ctx.info()));
            case "10" -> new RoomRealtimeEvent.StageRaiseHand(userId(ctx.info()), 1);
            case "11" -> new RoomRealtimeEvent.StageRaiseHand(userId(ctx.info()), 2);
            case "18" -> new RoomRealtimeEvent.StageInvite(userId(ctx.info()));
            case "23" -> new RoomRealtimeEvent.StageJoin(mapStageUser(ctx.info()));
            case "25" -> new RoomRealtimeEvent.Comment(mapComment(ctx.info()));
            case "29" -> new RoomRealtimeEvent.StageKick(userId(ctx.info()), textOr(ctx.info(), "manager_name", ""), cname(ctx.info()));
            case "30" -> new RoomRealtimeEvent.StageDeviceControl(userId(ctx.info()), 1, 1);
            case "34" -> new RoomRealtimeEvent.ModAccepted(userId(ctx.info()));
            case "35" -> new RoomRealtimeEvent.ModRemoved(userId(ctx.info()));
            case "40" -> new RoomRealtimeEvent.ModUnmuted(userId(ctx.info()));
            case "48" -> new RoomRealtimeEvent.ModInvite(userId(ctx.info()));
            case "53" -> new RoomRealtimeEvent.Follow(textOr(ctx.info(), "nickname", ""), ctx.info().path("status").asInt(0));
            case "3" -> mapTypeThree(ctx);
            default -> {
                log.info("LiveHub: unrecognized notify_type '{}' falling through to raw", ctx.type());
                yield raw(ctx.type(), ctx.root());
            }
        };
    }

    private boolean requiresUserId(FrameContext ctx) {
        if (TYPES_REQUIRING_USER_ID.contains(ctx.type())) {
            return ctx.info().path("user_id").asLong(0) == 0;
        }
        if (GIFT_TYPE.equals(ctx.type())) {
            JsonNode users = ctx.info().path("users");
            boolean isGiftBatch = ctx.info().path("type").asInt(-1) == 1
                && users.isArray() && !users.isEmpty();
            if (isGiftBatch) {
                JsonNode first = users.get(0);
                return !first.isObject() || first.path("send_uid").asLong(0) == 0;
            }
            // UserJoin path — drop if user_id is missing or zero
            return ctx.info().path("user_id").asLong(0) == 0;
        }
        return false;
    }

    private RoomRealtimeEvent luckyBagOrNull(FrameContext ctx) {
        return ctx.info().has("lucky_bag_id") ? raw(ctx.type(), ctx.root()) : null;
    }

    private RoomRealtimeEvent mapTypeThree(FrameContext ctx) {
        if (ctx.info().has("lucky_bag_id")) return raw(ctx.type(), ctx.root());
        int gameType = ctx.info().path("game_type").asInt(-1);
        int kickType = ctx.info().path("kick_type").asInt(-1);
        return switch (gameType) {
            case 2 -> new RoomRealtimeEvent.WhiteboardActivated(cname(ctx.info()));
            case 0 -> new RoomRealtimeEvent.WhiteboardDeactivated(cname(ctx.info()));
            default -> switch (kickType) {
                case 1 -> new RoomRealtimeEvent.RoomKick(
                    userId(ctx.info()), textOr(ctx.info(), "nickname", ""), textOr(ctx.info(), "manager_name", ""), cname(ctx.info()));
                case 2 -> new RoomRealtimeEvent.UserQuit(userId(ctx.info()));
                default -> raw(ctx.type(), ctx.root());
            };
        };
    }

    private RoomRealtimeEvent.Gift mapGiftBatch(JsonNode info) {
        JsonNode usersNode = info.path("users");
        if (info.path("type").asInt(-1) != 1 || !usersNode.isArray()) return null;
        List<RoomRealtimeEvent.GiftEvent> gifts = usersNode.valueStream()
            .filter(JsonNode::isObject)
            .map(userNode -> mapGift(userNode, info))
            .toList();
        return gifts.isEmpty() ? null : new RoomRealtimeEvent.Gift(gifts);
    }

    private RoomRealtimeEvent mapTypeOne(JsonNode info) {
        RoomRealtimeEvent gifts = mapGiftBatch(info);
        return gifts != null ? gifts
            : new RoomRealtimeEvent.UserJoin(userId(info), textOr(info, "nickname", textOr(info, "send_nickname", "Someone")));
    }

    private RoomRealtimeEvent.StageUserEvent mapStageUser(JsonNode info) {
        return new RoomRealtimeEvent.StageUserEvent(
            textOr(info, "user_id", null),
            textOr(info, "nickname", null),
            textOr(info, "head_url", null));
    }

    private RoomRealtimeEvent.GiftEvent mapGift(JsonNode userNode, JsonNode info) {
        return new RoomRealtimeEvent.GiftEvent(
            textOr(userNode, "send_uid", null),
            textOr(userNode, "send_nickname", null),
            textOr(userNode, "send_head_url", null),
            textOr(userNode, "send_nation", null),
            textOr(userNode, "receiver_uid", null),
            textOr(userNode, "receiver_nickname", null),
            textOr(userNode, "receiver_head_url", null),
            textOr(userNode, "receiver_nation", null),
            textOr(userNode, "small_pic", null),
            userNode.path("gift_id").asLong(0),
            userNode.path("gift_number").asInt(1),
            userNode.path("gift_val").asLong(0),
            info.path("vip_type").asInt(0),
            info.path("gift_level").asInt(0),
            info.path("day_rank_level").asInt(0));
    }

    private RoomRealtimeEvent.CommentEvent mapComment(JsonNode info) {
        JsonNode msg = info.path("msg");
        JsonNode textNode = msg.path("text");
        String text = textNode.isObject() ? textOr(textNode, "text", "") : textNode.asText("");

        String id = textOr(info, "_id", textOr(msg, "msg_id", ""));
        long ts = info.has("created_at") ? info.get("created_at").asLong() * 1000 : System.currentTimeMillis();

        return new RoomRealtimeEvent.CommentEvent(
            id,
            userId(info),
            textOr(info, "nickname", "Anonymous"),
            textOr(info, "head_url", ""),
            text,
            ts,
            mapReply(msg.get("reply_info")),
            textOr(info, "nationality", ""),
            info.path("role").asInt(3),
            info.path("vip_type").asInt(0),
            info.path("day_rank_level").asInt(0),
            info.path("gift_level").asInt(0),
            info.path("fg_level").asInt(0),
            textOr(info, "fg_name", ""),
            info.path("fg_is_active").asBoolean(false),
            info.path("bubble_id").asInt(-1),
            textOr(info, "bubble_url", ""),
            textOr(info, "bubble_color", "#ffffff"),
            info.path("hit_bad").asInt(0),
            info.path("bubble_animal_type").asInt(0),
            textOr(info, "bubble_animal_url", ""));
    }

    private RoomRealtimeEvent.ReplyInfoEvent mapReply(JsonNode replyNode) {
        if (replyNode == null || !replyNode.isObject()) return null;
        return new RoomRealtimeEvent.ReplyInfoEvent(
            textOr(replyNode, "msg_id", ""),
            replyNode.path("from_id").asLong(0),
            textOr(replyNode, "from_nickname", ""),
            textOr(replyNode, "text", ""),
            textOr(replyNode, "msg_type", "text"));
    }

    private RoomRealtimeEvent raw(String type, JsonNode root) {
        try {
            return new RoomRealtimeEvent.Raw(type, om.treeToValue(root, Object.class));
        } catch (java.io.IOException _) {
            return new RoomRealtimeEvent.Raw(type, root);
        }
    }

    private JsonNode readTreeOrNull(String text) {
        try {
            return om.readTree(text);
        } catch (Exception _) {
            return null;
        }
    }

    private static String userId(JsonNode info) {
        return textOr(info, "user_id", "");
    }

    private static String cname(JsonNode info) {
        return textOr(info, "cname", "");
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : fallback;
    }

    private record FrameContext(JsonNode info, String type, JsonNode root) {}
}
