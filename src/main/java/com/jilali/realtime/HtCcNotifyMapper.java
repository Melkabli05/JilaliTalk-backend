package com.jilali.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.platform.time.Seconds;
import com.jilali.realtime.dto.RoomCcRealtimeEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Parses LiveHub frames whose {@code notify_info} matches the AI-captioning / subtitle channel
 * ({@code LiveCCNotify} on Android) into {@link RoomCcRealtimeEvent}s.
 *
 * <p>The LiveHub socket carries both room events and CC events over the same WebSocket; the
 * discriminator is the {@code notify_info} shape — when it carries the subtitle-channel
 * fields ({@code nick_name}, {@code head_url} on the speaker, {@code role_type},
 * {@code expired_at}, {@code result_id}, …) it's a CC frame, otherwise it's a room frame.
 * {@link HtNotifyRouter} invokes this mapper for those frames.
 *
 * <p>Notify-type numbering on the CC channel is distinct from the room channel
 * (1 = start, 2 = end, 3 = disabled, 4 = line content, 6 = experience-card activated,
 * 12 = expired, plus the "kill" set {3, 5, 7, 9, 10, 11} that unconditionally resets subtitle
 * state on the Android client).
 */
@Singleton
public class HtCcNotifyMapper {

    private static final Logger log = LoggerFactory.getLogger(HtCcNotifyMapper.class);

    /** Notify-type values that the Android LiveSubTitleController unconditionally kills state on. */
    static final java.util.Set<Integer> CC_KILL_TYPES = java.util.Set.of(3, 5, 7, 9, 10, 11);

    /** The notify-type integer set the CC channel uses on the shared LiveHub socket. Same
     *  namespace as the room channel (the upstream multiplexes by frame shape), but
     *  completely different semantics. Numbers correspond to {@link RoomCcRealtimeEvent}'s
     *  per-case notify-type (1=start, 2=end, 3=disabled, 4=line, 5/7/9/10/11=kill-set,
     *  6=experience-card-activated, 12=expired). */
    static final java.util.Set<Integer> CC_TYPES = java.util.Set.of(1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12);

    private final ObjectMapper om;

    public HtCcNotifyMapper(ObjectMapper om) {
        this.om = om;
    }

    public Optional<RoomCcRealtimeEvent> map(String text) {
        JsonNode root;
        try {
            root = om.readTree(text);
        } catch (Exception _) {
            String snippet = text != null && text.length() > 120 ? text.substring(0, 120) + "…" : text;
            return Optional.of(new RoomCcRealtimeEvent.Error("Malformed LiveHub CC frame: " + snippet));
        }

        if (root == null) return Optional.empty();
        if (root.has("heartbeat_sec") || root.has("heartbeat_time")) return Optional.empty();

        JsonNode eventNode = root.get("event");
        if (eventNode == null || !eventNode.isObject()) return Optional.empty();

        JsonNode info = eventNode.path("notify_info");
        int type = eventNode.path("notify_type").asInt(-1);
        // Standalone-safe: re-run the discriminator here so direct callers of map() can't
        // accidentally claim a room-channel frame just because its notify_type overlaps.
        if (!CC_TYPES.contains(type)) return Optional.empty();
        if (info == null || !info.isObject() || !info.has("cname")) return Optional.empty();
        for (String roomKey : ROOM_MARKERS) {
            if (info.has(roomKey)) return Optional.empty();
        }
        // Either a CC marker is present (content/line frame), or the type is in the bare-cname
        // set: kill-set (3, 5, 7, 9, 10, 11), type 2 (end-of-stream), or type 6 (experience-card
        // activated, which always carries user_id but no CC marker on its own).
        boolean hasCcMarker = false;
        for (String ccKey : CC_MARKERS) {
            if (info.has(ccKey)) { hasCcMarker = true; break; }
        }
        if (!hasCcMarker) {
            boolean bareCnameType = (type == 2 || type == 6 || CC_KILL_TYPES.contains(type));
            if (!bareCnameType) return Optional.empty();
        }

        try {
            return Optional.ofNullable(mapEvent(type, info, root));
        } catch (Throwable _) {
            return Optional.of(new RoomCcRealtimeEvent.Error("Mapping failed for CC notify_type=" + type));
        }
    }

    /**
     * Cheap pre-check: does this frame belong to the CC channel?
     * Two conditions, both required:
     * <ol>
     *   <li>{@code notify_type} is in {@link #CC_TYPES} (the integer namespace the CC
     *       channel uses — note these overlap with room-channel notify_type integers, the
     *       upstream multiplexes on frame shape).</li>
     *   <li>The {@code notify_info} payload either carries at least one CC-specific key
     *       ({@code nick_name}, {@code role_type}, {@code result_id}, {@code expired_at},
     *       {@code enabled}, {@code create_at}, {@code update_at}) OR has only {@code cname}
     *       (the bare lifecycle frames: end-of-stream / disabled / kill-set).</li>
     * </ol>
     * If {@code notify_info} carries any room-specific key ({@code seat_id}, {@code lucky_bag_id},
     * {@code props_type}, {@code category_id}, {@code topic_id}, {@code game_type},
     * {@code kick_type}, {@code is_banned_comment}, {@code manager_name}), it belongs to the
     * room channel regardless — even if the type range overlaps.
     */
    public boolean ownsType(String text) {
        if (text == null || text.isEmpty()) return false;
        try {
            JsonNode root = om.readTree(text);
            JsonNode event = root == null ? null : root.get("event");
            if (event == null || !event.isObject()) return false;
            int type = event.path("notify_type").asInt(-1);
            if (!CC_TYPES.contains(type)) return false;
            JsonNode info = event.path("notify_info");
            if (info == null || !info.isObject() || !info.has("cname")) return false;
            // Room-channel markers — if any of these are present, the frame belongs to the room
            // channel even if the type happens to be in the CC range.
            for (String roomKey : ROOM_MARKERS) {
                if (info.has(roomKey)) return false;
            }
            // CC-specific markers — at least one of these is required for "content" frames.
            for (String ccKey : CC_MARKERS) {
                if (info.has(ccKey)) return true;
            }
            // No CC markers but also no room markers — accept only for the bare-cname set:
            // kill-set (3, 5, 7, 9, 10, 11), end-of-stream (2), experience-card activated (6).
            return type == 2 || type == 6 || CC_KILL_TYPES.contains(type);
        } catch (Exception _) {
            return false;
        }
    }

    private static final java.util.Set<String> ROOM_MARKERS = java.util.Set.of(
        "seat_id", "lucky_bag_id", "props_type", "category_id", "topic_id",
        "game_type", "kick_type", "is_banned_comment", "manager_name"
    );

    private static final java.util.Set<String> CC_MARKERS = java.util.Set.of(
        "nick_name", "role_type", "result_id", "expired_at", "enabled",
        "create_at", "update_at"
    );

    private RoomCcRealtimeEvent mapEvent(int type, JsonNode info, JsonNode root) {
        return switch (type) {
            case 1 -> new RoomCcRealtimeEvent.SubtitleStart(
                textOr(info, "cname", null),
                longOrNullString(info, "user_id"),
                textOr(info, "nick_name", null),
                textOr(info, "head_url", null),
                textOr(info, "nationality", null),
                info.path("role_type").asInt(0),
                textOr(info, "_id", null));
            case 2 -> new RoomCcRealtimeEvent.SubtitleEnd(textOr(info, "cname", null));
            case 3 -> new RoomCcRealtimeEvent.SubtitleDisabled(textOr(info, "cname", null));
            case 4 -> mapSubtitleLine(info);
            case 6 -> new RoomCcRealtimeEvent.SubtitleExperienceActivated(
                textOr(info, "cname", null),
                longOrNullString(info, "user_id"));
            case 12 -> new RoomCcRealtimeEvent.SubtitleExpired(
                textOr(info, "cname", null),
                // Android reads `expiredAt` as seconds since epoch (compared via `Date().time / 1000`).
                Seconds.toMillis(info.path("expired_at").asLong(0)));
            // 5/7/9/10/11 are in the kill set on Android — surface the raw frame so a future
            // frontend consumer can drive the same unconditional teardown if needed.
            default -> raw(type, root);
        };
    }

    private RoomCcRealtimeEvent.SubtitleLine mapSubtitleLine(JsonNode info) {
        return new RoomCcRealtimeEvent.SubtitleLine(
            textOr(info, "cname", null),
            textOr(info, "_id", null),
            textOr(info, "text", null),
            longOrNullString(info, "user_id"),
            textOr(info, "nick_name", null),
            textOr(info, "head_url", null),
            textOr(info, "nationality", null),
            info.path("role_type").asInt(0),
            Seconds.toMillis(info.path("create_at").asLong(0)),
            Seconds.toMillis(info.path("update_at").asLong(0)),
            info.path("result_id").asLong(0),
            info.has("enabled") && !info.path("enabled").isNull() ? info.path("enabled").asBoolean() : null,
            info.has("expired_at") && !info.path("expired_at").isNull() ? Seconds.toMillis(info.path("expired_at").asLong()) : null);
    }

    private RoomCcRealtimeEvent.Raw raw(int type, JsonNode root) {
        try {
            return new RoomCcRealtimeEvent.Raw(String.valueOf(type), om.treeToValue(root, Object.class));
        } catch (java.io.IOException _) {
            return new RoomCcRealtimeEvent.Raw(String.valueOf(type), root);
        }
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : fallback;
    }

    private static String longOrNullString(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        if (n.isTextual()) return n.asText();
        return String.valueOf(n.asLong());
    }
}
