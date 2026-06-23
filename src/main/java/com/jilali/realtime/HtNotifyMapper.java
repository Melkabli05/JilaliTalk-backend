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

    /**
     * LiveHub's {@code notify_type} is NOT globally unique — it's scoped per {@code cmd}. Real
     * per-user events arrive on {@code cmd 17923}; {@code cmd 17939} carries room-wide "enabled"
     * toggles on type 1 and 2 that share those codes but always have {@code user_id = 0}. The
     * reference client (scriptv2.js:4944) explicitly guards every per-user handler with
     * {@code user_id !== 0} — this set is exactly the type codes that need that guard. Gift
     * (1), lucky-bag (4/5/6), comment (25), and whiteboard/kick (3) have their own guard
     * semantics, so they're deliberately excluded.
     */
    private static final Set<String> TYPES_REQUIRING_USER_ID = Set.of(
        "2", "8", "9", "10", "11", "18", "23",
        "29", "30", "34", "35", "40", "48"
    );

    /** Type 1 needs the guard too, but its user_id lives inside {@code users[]} — special-cased. */
    private static final String GIFT_TYPE = "1";

    private final ObjectMapper om;

    public HtNotifyMapper(ObjectMapper om) {
        this.om = om;
    }

    public OptionalLong heartbeatSec(String text) {
        return Optional.ofNullable(readTreeOrNull(text))
            .filter(root -> root.has("heartbeat_sec"))
            .map(root -> OptionalLong.of(root.get("heartbeat_sec").asLong(60)))
            .orElse(OptionalLong.empty());
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

        if (requiresUserId(type, info)) {
            log.debug("LiveHub frame dropped: notify_type='{}' has user_id=0 (cmd-scoped noise event)", type);
            return Optional.empty();
        }

        try {
            RoomRealtimeEvent event = switch (type) {
                case GIFT_TYPE -> mapTypeOne(info);
                case "2" -> new RoomRealtimeEvent.UserQuit(userId(info));
                case "4" -> luckyBagOrNull(info, type, root) instanceof RoomRealtimeEvent.Raw r
                    ? r
                    : new RoomRealtimeEvent.StageJoin(mapStageUser(info));
                case "5" -> luckyBagOrNull(info, type, root) instanceof RoomRealtimeEvent.Raw r
                    ? r
                    : info.has("coin") ? null : new RoomRealtimeEvent.StageQuit(userId(info));
                case "6" -> luckyBagOrNull(info, type, root);
                case "8" -> new RoomRealtimeEvent.MicOpened(userId(info));
                case "9" -> new RoomRealtimeEvent.MicClosed(userId(info));
                case "10" -> new RoomRealtimeEvent.StageRaiseHand(userId(info), 1);
                case "11" -> new RoomRealtimeEvent.StageRaiseHand(userId(info), 2);
                case "18" -> new RoomRealtimeEvent.StageInvite(userId(info));
                case "23" -> new RoomRealtimeEvent.StageJoin(mapStageUser(info));
                case "25" -> new RoomRealtimeEvent.Comment(mapComment(info));
                case "29" -> new RoomRealtimeEvent.StageKick(userId(info), textOr(info, "manager_name", ""), cname(info));
                case "30" -> new RoomRealtimeEvent.StageDeviceControl(userId(info), 1, 1);
                case "34" -> new RoomRealtimeEvent.ModAccepted(userId(info));
                case "35" -> new RoomRealtimeEvent.ModRemoved(userId(info));
                case "40" -> new RoomRealtimeEvent.ModUnmuted(userId(info));
                case "48" -> new RoomRealtimeEvent.ModInvite(userId(info));
                case "53" -> new RoomRealtimeEvent.Follow(textOr(info, "nickname", ""), info.path("status").asInt(0));
                case "3" -> mapTypeThree(info, type, root);
                default -> {
                    log.info("LiveHub: unrecognized notify_type '{}' falling through to raw — server may have added a new type", type);
                    yield raw(type, root);
                }
            };
            return Optional.ofNullable(event);
        } catch (Throwable t) {
            // Throwable, not Exception: a class-shape mismatch from a recompile racing this
            // long-lived connection (NoClassDefFoundError/LinkageError on one of the records
            // above) is an Error, not an Exception — left uncaught here, it escapes this one
            // frame and tears down the whole upstream socket for every subscriber in the room.
            log.warn("LiveHub event mapping failed for notify_type='{}': {}", type, t.getMessage());
            return Optional.of(new RoomRealtimeEvent.Error("Mapping failed for notify_type=" + type + ": " + t.getMessage()));
        }
    }

    /**
     * True when this frame must carry a real (non-zero) user_id to be a real event. Most per-user
     * types carry it directly on {@code notify_info.user_id}; only the gift subshape of type 1
     * (signalled by an inner {@code type:1} + non-empty {@code users[]} array) carries it on
     * {@code users[0].send_uid}. A zero or absent id means the frame is a cmd-scoped noise event
     * (e.g. a cmd 17939 enabled-toggle) and must be dropped.
     */
    private boolean requiresUserId(String type, JsonNode info) {
        if (TYPES_REQUIRING_USER_ID.contains(type)) {
            return info.path("user_id").asLong(0) == 0;
        }
        if (GIFT_TYPE.equals(type)) {
            // Sequenced-collection style: pull the first user via the stream API rather than the
            // positional `users.get(0)`. Same answer, intent ("look at the first gift sender")
            // is now explicit in the code.
            return info.path("users").valueStream()
                .filter(JsonNode::isObject)
                .findFirst()
                .map(first -> first.path("send_uid").asLong(0) == 0)
                .orElse(false);
        }
        return false;
    }

    /** Lucky-bag events on types 4/5/6 share the same shape: pass-through as {@link RoomRealtimeEvent.Raw}. */
    private RoomRealtimeEvent luckyBagOrNull(JsonNode info, String type, JsonNode root) throws Exception {
        return info.has("lucky_bag_id") ? raw(type, root) : null;
    }

    /** Type 3 routes by {@code game_type} (whiteboard) and {@code kick_type} (room kick / voluntary leave). */
    private RoomRealtimeEvent mapTypeThree(JsonNode info, String type, JsonNode root) throws Exception {
        if (info.has("lucky_bag_id")) return raw(type, root);
        int gameType = info.path("game_type").asInt(-1);
        int kickType = info.path("kick_type").asInt(-1);
        return switch (gameType) {
            case 2 -> new RoomRealtimeEvent.WhiteboardActivated(cname(info));
            case 0 -> new RoomRealtimeEvent.WhiteboardDeactivated(cname(info));
            default -> switch (kickType) {
                // kick_type 1 carries manager_name — a real mod action.
                case 1 -> new RoomRealtimeEvent.RoomKick(
                    userId(info), textOr(info, "nickname", ""), textOr(info, "manager_name", ""), cname(info));
                // kick_type 2 carries no manager_name — the user left on their own, same event as type 2.
                case 2 -> new RoomRealtimeEvent.UserQuit(userId(info));
                default -> raw(type, root);
            };
        };
    }

    private RoomRealtimeEvent.Gift mapGiftBatch(JsonNode info) throws Exception {
        JsonNode usersNode = info.path("users");
        if (info.path("type").asInt(-1) != 1 || !usersNode.isArray()) return null;
        // `Stream.toList()` returns an unmodifiable List — a SequencedCollection since JEP 431 —
        // we don't add to it after construction, so the immutability is a free bonus here. Same
        // wire order, same zero-arg semantics, none of the ArrayList ceremony.
        List<RoomRealtimeEvent.GiftEvent> gifts = usersNode.valueStream()
            .filter(JsonNode::isObject)
            .map(userNode -> mapGift(userNode, info))
            .toList();
        return gifts.isEmpty() ? null : new RoomRealtimeEvent.Gift(gifts);
    }

    private RoomRealtimeEvent mapTypeOne(JsonNode info) throws Exception {
        RoomRealtimeEvent gifts = mapGiftBatch(info);
        if (gifts != null) return gifts;
        return new RoomRealtimeEvent.UserJoin(
            userId(info),
            textOr(info, "nickname", textOr(info, "send_nickname", "Someone")));
    }

    /**
     * {@code RoomRealtimeEvent.StageUserEvent} carries no {@code @JsonProperty} overrides (its
     * fields are already camelCase for the outbound WS frame to the frontend), so unlike most of
     * this mapper it cannot be built via {@code om.treeToValue} against the upstream's snake_case
     * {@code notify_info} — it has to be read out field by field, same as every other event here.
     */
    private RoomRealtimeEvent.StageUserEvent mapStageUser(JsonNode info) {
        return new RoomRealtimeEvent.StageUserEvent(
            textOr(info, "user_id", null),
            textOr(info, "nickname", null),
            textOr(info, "head_url", null));
    }

    /**
     * Per-gift fields (id/number/val/avatars/nations) live on {@code userNode} (the entry inside
     * {@code notify_info.users[]}); the sender's level/VIP/rank are siblings of {@code users}
     * on the outer {@code notify_info} — confirmed against a captured live frame where
     * {@code notify_info.gift_id} is always {@code 0} (a decoy) while the real id is
     * {@code users[0].gift_id}.
     */
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

        // The real frame carries the comment's id and creation time directly on `info` (`_id`,
        // `created_at` in Unix seconds) — not nested under `msg` as `msg_id`/`send_ts`, which
        // this mapper looked for previously. Neither exists on this payload shape, so `id` was
        // always blank and `ts` always fell back to "now" (BFF receipt time) instead of the
        // comment's actual send time.
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
            textOr(info, "nationality", null),
            info.path("role").asInt(3),
            info.path("vip_type").asInt(0),
            info.path("day_rank_level").asInt(0),
            info.path("gift_level").asInt(0),
            info.path("fg_level").asInt(0),
            textOr(info, "fg_name", ""),
            info.path("fg_is_active").asBoolean(false),
            info.path("bubble_id").asInt(-1),
            textOr(info, "bubble_url", null),
            textOr(info, "bubble_color", "#ffffff"),
            info.path("hit_bad").asInt(0),
            info.path("bubble_animal_type").asInt(0),
            textOr(info, "bubble_animal_url", null));
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

    private RoomRealtimeEvent raw(String type, JsonNode root) throws Exception {
        return new RoomRealtimeEvent.Raw(type, om.treeToValue(root, Object.class));
    }

    private JsonNode readTreeOrNull(String text) {
        try {
            return om.readTree(text);
        } catch (Exception e) {
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

    private static String abbrev(String s) {
        return s != null && s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }
}
