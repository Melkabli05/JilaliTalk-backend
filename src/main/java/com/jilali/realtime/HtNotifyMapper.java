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
            // startwebsock() also requires !obj.notify_play_room here (root-level field,
            // shared with the gift-animation-tier indicator on type "1"/"25") — a type-2
            // push carrying it is a gift-flow artifact, not a genuine room departure.
            case "2" -> ctx.root().has("notify_play_room") ? null : new RoomRealtimeEvent.UserQuit(userId(ctx.info()));
            case "4" -> {
                RoomRealtimeEvent e = luckyBagOrNull(ctx);
                yield e != null ? e : new RoomRealtimeEvent.StageJoin(mapStageUser(ctx.info()));
            }
            // startwebsock() gates stage-quit on notify_info.seat_id === 0 — confirmed against
            // a real captured type-5 payload embedded in that source (seat_id: 0, no "coin"
            // field at all), which is the more authoritative dispatcher than the ghost-mode
            // fireRoomWebSocket()'s separate "!coin" heuristic this used to check instead.
            case "5" -> {
                RoomRealtimeEvent e = luckyBagOrNull(ctx);
                yield e != null ? e : (ctx.info().path("seat_id").asInt(-1) == 0 ? new RoomRealtimeEvent.StageQuit(userId(ctx.info())) : null);
            }
            case "6" -> luckyBagOrNull(ctx);
            case "7"  -> mapPropsApplied(ctx);
            case "8" -> new RoomRealtimeEvent.MicOpened(userId(ctx.info()));
            case "9" -> new RoomRealtimeEvent.MicClosed(userId(ctx.info()));
            case "10" -> new RoomRealtimeEvent.StageRaiseHand(userId(ctx.info()), 1);
            case "11" -> new RoomRealtimeEvent.StageRaiseHand(userId(ctx.info()), 2);
            case "18" -> new RoomRealtimeEvent.StageInvite(userId(ctx.info()));
            case "23" -> new RoomRealtimeEvent.StageJoin(mapStageUser(ctx.info()));
            case "25" -> new RoomRealtimeEvent.Comment(mapComment(ctx.info()));
            // startwebsock() gates this on notify_info.kick_type === 1 (shares the same
            // "remove from stage" handling as the seat_id===0 case above it) — not every
            // type-29 push is necessarily a stage kick.
            case "29" -> ctx.info().path("kick_type").asInt(-1) == 1
                ? new RoomRealtimeEvent.StageKick(userId(ctx.info()), textOr(ctx.info(), "manager_name", ""), cname(ctx.info()))
                : null;
            case "30" -> new RoomRealtimeEvent.StageDeviceControl(userId(ctx.info()), 1, 1);
            case "34" -> new RoomRealtimeEvent.ModAccepted(userId(ctx.info()));
            case "35" -> new RoomRealtimeEvent.ModRemoved(userId(ctx.info()));
            case "40" -> new RoomRealtimeEvent.ModUnmuted(userId(ctx.info()));
            case "47" -> mapTopicShare(ctx.info());
            case "48" -> new RoomRealtimeEvent.ModInvite(userId(ctx.info()));
            case "53" -> new RoomRealtimeEvent.Follow(
                userId(ctx.info()), textOr(ctx.info(), "nickname", ""), textOr(ctx.info(), "head_url", null),
                ctx.info().path("status").asInt(0));
            case "3" -> mapTypeThree(ctx);
            // The Android live fragment switches on a wider set of notify_types than startwebsock().
            // Without captured frames we can't commit to a hard mapping, but the wire shapes of
            // the unmapped entities (LiveWSSGiftWish, LiveWSSTreasureReward, LiveWSSPurchaseVip,
            // LiveWSSReceiveVipGifts, FgUpgradeAward) all carry a distinguishing key in their
            // notify_info — so we route by SHAPE rather than guessing notify_type, then fall
            // back to Raw if the shape doesn't match. Real captures can tighten these to
            // direct notify_type cases later.
            case "12", "28", "50", "58", "59" -> mapByEntityShape(ctx);
            default -> {
                log.info("LiveHub: unrecognized notify_type '{}' falling through to raw", ctx.type());
                yield raw(ctx.type(), ctx.root());
            }
        };
    }

    /**
     * Inspect the {@code notify_info} shape to figure out which unmapped entity (GiftWish,
     * TreasureReward, PurchaseVip, ReceiveVipGifts, FgUpgradeAward) the frame belongs to. The
     * Android client receives these on notify_types 12, 28, 50, 58, 59 but the upstream has
     * not been observed sending them on those exact types; routing by shape means we
     * surface typed events the moment any frame arrives whose keys match, regardless of which
     * notify_type the server picked.
     *
     * <p>If the shape doesn't match any of the known entities, fall through to Raw so the
     * frame is preserved instead of silently dropped.
     */
    private RoomRealtimeEvent mapByEntityShape(FrameContext ctx) {
        JsonNode info = ctx.info();

        // GiftWish — has received_gift_count + config_gift_count (its unique signature).
        if (info.has("received_gift_count") && info.has("config_gift_count")) {
            return mapGiftWish(info);
        }
        // TreasureReward — has camp_result OR reward_info sub-objects (composed shapes).
        if (info.has("camp_result") || info.has("reward_info") || info.has("reward_popup_color")) {
            return mapTreasureReward(info);
        }
        // PurchaseVip — has gift_name + small_pic + label (banner gift shape, no reward_list).
        if (info.has("gift_name") && info.has("label") && info.has("small_pic") && info.has("title")
                && !info.has("reward_list")) {
            return mapPurchaseVip(info);
        }
        // ReceiveVipGifts — has send_nick_name + send_type + vip_time.
        if (info.has("send_nick_name") && info.has("vip_time")) {
            return mapReceiveVipGifts(info);
        }
        // FgUpgradeAward — has id + typ + icon + content (family-group tier-upgrade toast).
        if (info.has("typ") && info.has("icon") && info.has("content")) {
            return mapFgUpgradeAward(info);
        }

        return raw(ctx.type(), ctx.root());
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
        return ctx.info().has("lucky_bag_id") ? mapLuckyBag(ctx.info()) : null;
    }

    private RoomRealtimeEvent mapLuckyBag(JsonNode info) {
        return new RoomRealtimeEvent.LuckyBag(
            textOr(info, "lucky_bag_id", ""),
            info.path("lucky_bag_number").asInt(0),
            cname(info));
    }

    private RoomRealtimeEvent mapTypeThree(FrameContext ctx) {
        if (ctx.info().has("lucky_bag_id")) return mapLuckyBag(ctx.info());
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

    /**
     * Type 47 — topic/category share dropped into the room. Wire shape (confirmed from a live
     * capture): {@code {category_id, cname, name, topic_id}}. Tapping the rendered card on the
     * client deep-links into the topic page identified by {@code topic_id}; the
     * {@code category_id} is the parent topic category.
     */
    private RoomRealtimeEvent mapTopicShare(JsonNode info) {
        return new RoomRealtimeEvent.RoomTopicShare(
            cname(info),
            info.path("category_id").asLong(0),
            info.path("topic_id").asLong(0),
            textOr(info, "name", ""));
    }

    /**
     * Type 7 — props / bubble skin applied in the room by a user. Wire shape (confirmed from a
     * live capture): {@code {animal_type, animal_url_v2, background_paid, cname,
     * list_background_url, props_id, props_type, room_big_background_url, sound_wave_url,
     * top_list_background_url, user_id}}. The {@code background_paid} field is a price tier —
     * higher numbers unlock more effects; the Android client gates animation play on it.
     */
    private RoomRealtimeEvent mapPropsApplied(FrameContext ctx) {
        JsonNode info = ctx.info();
        return new RoomRealtimeEvent.RoomPropsApplied(
            cname(info),
            userId(info),
            info.path("props_id").asLong(0),
            info.path("props_type").asInt(0),
            info.path("animal_type").asInt(0),
            textOr(info, "animal_url_v2", null),
            textOr(info, "list_background_url", null),
            textOr(info, "room_big_background_url", null),
            textOr(info, "sound_wave_url", null),
            textOr(info, "top_list_background_url", null),
            info.path("background_paid").asInt(0));
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
        if (gifts != null) return gifts;
        boolean isBannedComment = info.path("is_banned_comment").asBoolean(false);
        return new RoomRealtimeEvent.UserJoin(
            userId(info),
            textOr(info, "nickname", textOr(info, "send_nickname", "Someone")),
            textOr(info, "head_url", null),
            textOr(info, "nationality", null),
            isBannedComment);
    }

    /**
     * Full extractor for {@code LiveWSSRoomUser} into {@link RoomRealtimeEvent.StageUserEvent}.
     * Earlier mapper only forwarded {@code user_id/nickname/head_url}, silently dropping every
     * visual-identity field the server actually sends on a {@code notify_type: 4 / 23} push —
     * so the frontend had no role / seat / VIP-crown / bubble / FG / enter-effect to render
     * until a REST roster pull repainted them. Every field here is a real key on the Android
     * {@code LiveWSSRoomUser} Gson-annotated entity; absent keys yield zero-equivalent
     * defaults (null / 0 / false) so the frontend can detect "unknown" cheaply.
     */
    private RoomRealtimeEvent.StageUserEvent mapStageUser(JsonNode info) {
        return new RoomRealtimeEvent.StageUserEvent(
            textOr(info, "user_id", null),
            textOr(info, "nickname", null),
            textOr(info, "head_url", null),
            textOr(info, "nationality", null),
            info.path("role").asInt(3),
            info.path("seat_id").asInt(0),
            textOr(info, "vip_logo", null),
            textOr(info, "vip_logo_anim", null),
            info.path("vip_type").asInt(0),
            info.path("day_rank_level").asInt(0),
            info.path("gift_level").asInt(0),
            info.path("g_type").asInt(0),
            info.path("screen_share_uid").asLong(0),
            info.path("enter_effect_id").asInt(0),
            info.path("enter_effect_animal_type").asInt(0),
            textOr(info, "enter_effect_animal_url", null),
            info.path("enter_effect_paid").asInt(0),
            info.path("ripple_animal_type").asInt(0),
            textOr(info, "ripple_animal_url", null),
            textOr(info, "ripple_url", null),
            info.path("is_on_stage").asBoolean(false),
            info.path("is_first_join").asBoolean(false),
            info.path("is_in_room").asBoolean(false),
            info.path("is_turn_on_mic").asBoolean(false),
            info.path("is_turn_on_cam").asBoolean(false),
            textOr(info, "invite_user_id", null),
            textOr(info, "invite_nickname", null),
            textOr(info, "invite_head_url", null),
            textOr(info, "invite_nationality", null),
            info.path("bubble_id").asInt(-1),
            textOr(info, "bubble_url", null),
            textOr(info, "bubble_color", "#ffffff"),
            info.path("bubble_animal_type").asInt(0),
            textOr(info, "bubble_animal_url", null),
            info.path("fg_level").asInt(0),
            textOr(info, "fg_name", null),
            info.path("fg_is_active").asBoolean(false),
            info.path("follower_id").asLong(0),
            info.path("followee_id").asLong(0),
            info.path("audience_total").asInt(0),
            info.path("raise_hand_count").asInt(0),
            textOr(info, "medal_wall_icon", null),
            info.path("joinTime").asLong(0),
            info.path("created_at").asLong(0) * 1000L,
            info.path("pinned_status").asInt(0),
            textOr(info, "pinned_type", null),
            info.path("team_index").asInt(0),
            info.path("status").asInt(0),
            info.path("type").asInt(0),
            textOr(info, "name", null),
            textOr(info, "label", null),
            info.path("level").asInt(0),
            textOr(info, "reason", null),
            textOr(info, "notice", null),
            textOr(info, "tip_text", null),
            textOr(info, "share_status", null),
            textOr(info, "location", null));
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

        // server_ts and send_time are siblings on the Android LiveWSSMessage sub-object; also
        // surface msg_model/source/from_profile_ts which the comment renderer uses to pick a
        // variant and gate the profile-tour badge.
        long serverTime = msg.path("server_ts").asLong(0) * 1000L;
        long sendTime   = msg.path("send_time").asLong(0) * 1000L;

        return new RoomRealtimeEvent.CommentEvent(
            id,
            textOr(msg, "msg_id", null),
            userId(info),
            textOr(info, "nickname", "Anonymous"),
            textOr(info, "head_url", ""),
            text,
            ts,
            serverTime,
            sendTime,
            textOr(msg, "msg_model", null),
            textOr(msg, "source", null),
            msg.path("from_profile_ts").asInt(0),
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
            textOr(replyNode, "msg_type", "text"),
            replyNode.path("send_time").asLong(0) * 1000L);
    }

    // ---- Whole-entity mappers for unmapped LiveWSS* / LiveCCNotify siblings ----
    //
    // These mirror Android entities that don't yet ride a known notify_type from the live room
    // socket (their notify_type is unknown until a captured frame is matched). They can be
    // invoked today from a future case branch; for now the HtNotifyMapperTest exercises them
    // directly so the JSON parsing stays correct.

    /** LiveWSSGiftWish — goal-gift running total. */
    RoomRealtimeEvent.GiftWish mapGiftWish(JsonNode info) {
        return new RoomRealtimeEvent.GiftWish(
            info.path("gift_id").asLong(0),
            textOr(info, "small_pic", null),
            info.path("config_gift_count").asInt(0),
            info.path("received_gift_count").asInt(0),
            info.path("virtual_val").asLong(0));
    }

    /** LiveWSSReward — single reward entry inside a show popup / reward-list. */
    RoomRealtimeEvent.Reward mapReward(JsonNode node) {
        return new RoomRealtimeEvent.Reward(
            node.path("reward_id").asLong(0),
            node.path("award_type").asInt(0),
            textOr(node, "name", null),
            node.path("number").asInt(0),
            node.path("animal_type").asInt(0),
            textOr(node, "animal_url", null),
            node.path("virtual_val").asLong(0),
            node.path("is_mystery_gift").asBoolean(false));
    }

    /** LiveWSSRewardInfo — per-user reward envelope wrapping a {@code reward_list} array. */
    RoomRealtimeEvent.RewardInfo mapRewardInfo(JsonNode info) {
        JsonNode rl = info.path("reward_list");
        List<RoomRealtimeEvent.Reward> rewards = new ArrayList<>();
        if (rl.isArray()) {
            for (JsonNode r : rl) {
                if (r.isObject()) rewards.add(mapReward(r));
            }
        }
        return new RoomRealtimeEvent.RewardInfo(
            textOr(info, "user_id", null),
            textOr(info, "nickname", null),
            textOr(info, "head_url", null),
            textOr(info, "nation", null),
            rewards);
    }

    /** LiveWSSPurchaseVip — VIP-purchase banner piggybacked onto the room channel. */
    RoomRealtimeEvent.PurchaseVip mapPurchaseVip(JsonNode info) {
        return new RoomRealtimeEvent.PurchaseVip(
            cname(info),
            textOr(info, "send_uid", null),
            info.path("gift_id").asLong(0),
            textOr(info, "gift_name", null),
            info.path("gift_type").asInt(0),
            info.path("gift_number").asInt(0),
            textOr(info, "label", null),
            textOr(info, "small_pic", null),
            textOr(info, "title", null));
    }

    /** LiveWSSReceiveVipGifts — recipient side of a VIP-gift transaction. */
    RoomRealtimeEvent.ReceiveVipGifts mapReceiveVipGifts(JsonNode info) {
        return new RoomRealtimeEvent.ReceiveVipGifts(
            cname(info),
            textOr(info, "send_user_id", null),
            textOr(info, "send_nick_name", null),
            info.path("send_type").asInt(0),
            info.path("vip_time").asLong(0) * 1000L,
            info.path("show_time").asLong(0) * 1000L);
    }

    /** LiveWSSCampResultEntity nested under {@code LiveWSSTreasureReward.camp_result}. */
    RoomRealtimeEvent.CampResult mapCampResult(JsonNode node) {
        return new RoomRealtimeEvent.CampResult(
            textOr(node, "option_left_name", null),
            textOr(node, "option_right_name", null),
            node.path("option_result").asInt(0),
            node.path("vote_count_left").asInt(0),
            node.path("vote_count_right").asInt(0));
    }

    /** LiveWSSTreasureReward — the big treasure popup (composes CampResult + RewardInfo). */
    RoomRealtimeEvent.TreasureReward mapTreasureReward(JsonNode info) {
        JsonNode cr = info.path("camp_result");
        JsonNode ri = info.path("reward_info");
        return new RoomRealtimeEvent.TreasureReward(
            textOr(info, "title", null),
            cr.isObject() ? mapCampResult(cr) : null,
            ri.isObject() ? mapRewardInfo(ri) : null,
            textOr(info, "task_type_new", null),
            info.path("open_cycle").asInt(0),
            info.path("open_level").asInt(0),
            info.path("animal_type").asInt(0),
            textOr(info, "animal_url", null),
            stringList(info.path("participate_user_ids")),
            stringList(info.path("reward_user_ids")),
            stringList(info.path("no_privilege_user_ids")),
            textOr(info, "reward_popup_color", null),
            textOr(info, "main_text_color", null),
            textOr(info, "sub_text_color", null),
            textOr(info, "task_desc_color", null));
    }

    /** FgUpgradeAward — family-group tier-upgrade reward toast. Reads the wire key {@code typ}
     *  (the Android SerializedName on LiveWSSRewardInfo's kind field) and emits it as
     *  {@code awardType} on the JSON the frontend consumes — the field is renamed in the
     *  DTO to avoid colliding with the {@code type} discriminator on the sealed union. */
    RoomRealtimeEvent.FgUpgradeAward mapFgUpgradeAward(JsonNode info) {
        return new RoomRealtimeEvent.FgUpgradeAward(
            info.path("id").asLong(0),
            textOr(info, "typ", null),
            textOr(info, "icon", null),
            textOr(info, "content", null));
    }

    private static List<String> stringList(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> out = new ArrayList<>(node.size());
        for (JsonNode n : node) {
            if (n.isTextual()) out.add(n.asText());
        }
        return out;
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
