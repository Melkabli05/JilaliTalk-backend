package com.jilali.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.jilali.im.HtImPacketFramer.Header;
import com.jilali.im.dto.ImRealtimeEvent;

/**
 * Maps decoded JSON push payloads from the personal ht_im/sock channel to {@link ImRealtimeEvent}s.
 * Pure — no networking, no mutable state. The notify_info carries no user_id at all for personal
 * notify types (confirmed via live capture of a real notify_type 48 frame), so userId falls back
 * to the connector's own identity. Mirrors {@link com.jilali.realtime.HtNotifyMapper}'s pattern.
 */
final class HtImNotifyMapper {

    private final long selfUserId;

    HtImNotifyMapper(long selfUserId) {
        this.selfUserId = selfUserId;
    }

    ImRealtimeEvent map(JsonNode root, Header h) {
        if (root.has("msg_type")) {
            return switch (root.path("msg_type").asText()) {
                case "text"              -> mapText(root, h);
                case "image"             -> mapImage(root, h);
                // Real wire value is "send_gift", not "gift" — confirmed at
                // y41/l.smali:282 (const-string v0, "send_gift" registered immediately
                // before ChatSendGiftDelegate at line 284, the same pattern every other
                // msg_type entry in that dispatch table follows) and independently in
                // old_hellotalk/scriptv2.js:4643 (`if (type === "send_gift")`). Our own
                // outbound sender (ImSendController.java:140) already emits "send_gift"
                // correctly — this inbound switch was the only place with the wrong
                // label, silently dropping every incoming gift DM to `default -> null`.
                case "send_gift"         -> mapGift(root, h);
                case "introduction"      -> mapIntro(root, h);
                case "voice_room"        -> mapVoiceRoom(root, h);
                case "live_link"         -> mapLiveLink(root, h);
                case "new_voice_visitor" -> mapProfileVisit(root, h);
                default                  -> null;
            };
        }
        if (root.has("notify_type")) return mapNotify(root, h);
        return null;
    }

    private ImRealtimeEvent mapText(JsonNode root, Header h) {
        String fromId = textOr(root, "from_id", String.valueOf(h.fromId()));
        String fromNickname = textOr(root, "from_nickname", textOr(root, "nickname", ""));
        String fromHeadUrl = root.path("from_head_url").isNull() ? null : textOr(root, "from_head_url", null);
        JsonNode t = root.path("text");
        String text = t.isObject() ? textOr(t, "text", "") : t.asText("");
        long ts = root.path("ts").asLong(System.currentTimeMillis());
        String msgId = nullableText(root, "msg_id");
        return new ImRealtimeEvent.TextMessage(fromId, fromNickname, fromHeadUrl, text, ts, msgId);
    }

    private ImRealtimeEvent mapImage(JsonNode root, Header h) {
        String fromId = textOr(root, "from_id", String.valueOf(h.fromId()));
        String fromNickname = textOr(root, "from_nickname", textOr(root, "nickname", ""));
        String fromHeadUrl = root.path("from_head_url").isNull() ? null : textOr(root, "from_head_url", null);
        String url = root.path("image").path("url").asText("");
        if (url.isBlank()) url = textOr(root, "image_url", "");
        long ts = root.path("ts").asLong(System.currentTimeMillis());
        String msgId = nullableText(root, "msg_id");
        return new ImRealtimeEvent.ImageMessage(fromId, fromNickname, fromHeadUrl, url, ts, msgId);
    }

    private ImRealtimeEvent mapGift(JsonNode root, Header h) {
        String fromId       = textOr(root, "from_id", String.valueOf(h.fromId()));
        String fromNickname = textOr(root, "from_nickname", textOr(root, "nickname", ""));
        String fromHeadUrl  = textOr(root, "from_head_url", textOr(root, "head_url", ""));
        long   giftId       = root.path("gift_id").asLong(0);
        int    count        = root.path("gift_number").asInt(1);
        String msgId        = nullableText(root, "msg_id");
        return new ImRealtimeEvent.GiftMessage(fromId, fromNickname, fromHeadUrl, giftId, count, msgId);
    }

    /**
     * The target profile arrives nested under {@code introduction.user_profile} — confirmed
     * directly from the APK's {@code IMIntroductionBean$UserProfile} class (Gson
     * {@code @SerializedName} annotations on the actual fields: {@code user_id}, {@code
     * nick_name}, {@code head_url}, {@code sex} (Integer), {@code age}, {@code country} — there
     * is no {@code nationality} or {@code bio} field on this class at all, unlike earlier
     * client code assumed). {@code profileNode} still falls back to a flat {@code introduction}
     * shape defensively in case a legacy/alternate server build sends it un-nested.
     */
    private ImRealtimeEvent mapIntro(JsonNode root, Header h) {
        String fromId       = textOr(root, "from_id", String.valueOf(h.fromId()));
        String fromNickname = textOr(root, "from_nickname", textOr(root, "nickname", ""));
        String fromHeadUrl  = textOr(root, "from_head_url", textOr(root, "head_url", ""));
        String msgId        = nullableText(root, "msg_id");

        JsonNode intro = root.path("introduction");
        JsonNode profile = intro.path("user_profile");
        JsonNode profileNode = profile.isMissingNode() || profile.isNull() ? intro : profile;

        String targetUserId = textOr(profileNode, "user_id", "");
        if (targetUserId.isEmpty()) return null;

        return new ImRealtimeEvent.IntroductionMessage(
            fromId, fromNickname, fromHeadUrl,
            targetUserId,
            textOr(profileNode, "nick_name", textOr(profileNode, "nickname", "")),
            nullableText(profileNode, "head_url"),
            nullableAny(profileNode, "sex"),
            profileNode.hasNonNull("age") ? profileNode.path("age").asInt() : null,
            nullableText(profileNode, "country", "nationality"),
            nullableText(profileNode, "bio"),
            msgId);
    }

    /**
     * DM-channel voice-room share — live-captured shape (an offline packet_list entry, cmdId
     * 0x4001, decoded via {@code OfflineSingleChatRequest}'s packet_list, uid 169335562):
     * <pre>
     * {"voice_room":{"user_id":168995401,"head_url":"...","cname":"VR_...","active_number":0,
     *                "from_nickname":"Mikasa", ...},
     *  "from_nickname":"Mikasa","from_profile_ts":1,"msg_id":"...","msg_model":"normal",
     *  "msg_type":"voice_room","send_time":"...","server_time":"...","server_ts":...}
     * </pre>
     * This is a DIFFERENT wire shape from {@link #mapNotify}'s {@code root.has("cname")} branch
     * (the LiveHub notify_type-style room-share push, cname at the JSON root) — here the room
     * payload is nested under a {@code voice_room} key and dispatch is keyed by top-level
     * {@code msg_type}, exactly mirroring the outbound shape {@code prvgmsgpacket.js}'s
     * {@code sendTextMessage} builds for {@code type === 'voice_room'} ({@code msg.voice_room =
     * roomData}). Before this case existed, every voice-room DM share silently vanished — the
     * switch in {@link #map} fell through to {@code default -> null} for any {@code msg_type}
     * it didn't recognize, dropping the event before it ever reached {@link #mapNotify}.
     */
    private ImRealtimeEvent mapVoiceRoom(JsonNode root, Header h) {
        JsonNode room = root.path("voice_room");
        String fromId       = textOr(root, "from_id", String.valueOf(h.fromId()));
        String fromNickname = textOr(root, "from_nickname", textOr(room, "from_nickname", ""));
        String headUrl       = nullableText(room, "head_url");
        String cname         = textOr(room, "cname", "");
        String msgId          = nullableText(root, "msg_id");
        int count             = room.path("active_number").asInt(0);
        return new ImRealtimeEvent.VoiceRoomShared(fromId, fromNickname, cname, headUrl, count, msgId);
    }

    /** Live/video-room counterpart of {@link #mapVoiceRoom} — same envelope shape with the room
     *  payload nested under {@code live_link} instead of {@code voice_room}, matching
     *  {@code prvgmsgpacket.js}'s {@code type === 'live_link'} outbound case. */
    private ImRealtimeEvent mapLiveLink(JsonNode root, Header h) {
        JsonNode room = root.path("live_link");
        String fromId       = textOr(root, "from_id", String.valueOf(h.fromId()));
        String fromNickname = textOr(root, "from_nickname", textOr(room, "from_nickname", ""));
        String headUrl       = nullableText(room, "head_url");
        String cname         = textOr(room, "cname", "");
        String msgId          = nullableText(root, "msg_id");
        return new ImRealtimeEvent.LiveRoomShared(fromId, fromNickname, cname, headUrl, msgId);
    }

    /** Like {@link #nullableText}, but converts non-string JSON values (e.g. {@code sex} is an
     *  Integer on the wire per the APK's UserProfile class) instead of treating them as absent. */
    private static String nullableAny(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : null;
    }

    private static String nullableText(JsonNode node, String field, String fallbackField) {
        String v = nullableText(node, field);
        return v != null ? v : nullableText(node, fallbackField);
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n != null && n.isTextual()) ? n.asText() : null;
    }

    private ImRealtimeEvent mapNotify(JsonNode root, Header h) {
        if (root.has("cname")) {
            String fromId        = textOr(root, "from_id", String.valueOf(h.fromId()));
            String cname        = textOr(root, "cname", "");
            String fromNickname = textOr(root, "from_nickname", textOr(root, "nickname", ""));
            String headUrl      = root.path("head_url").isNull() ? null : textOr(root, "head_url", null);
            String msgId        = nullableText(root, "msg_id");
            if (root.has("count") || root.has("voice_count")) {
                int cnt = root.has("count") ? root.path("count").asInt(0) : root.path("voice_count").asInt(0);
                return new ImRealtimeEvent.VoiceRoomShared(fromId, fromNickname, cname, headUrl, cnt, msgId);
            }
            return new ImRealtimeEvent.LiveRoomShared(fromId, fromNickname, cname, headUrl, msgId);
        }

        JsonNode info = root.path("notify_info");
        String selfId = String.valueOf(selfUserId);
        switch (root.path("notify_type").asText("")) {
            case "18":
                return new ImRealtimeEvent.StageInvite(textOr(info, "user_id", selfId), textOr(info, "cname", ""));
            case "48":
                return new ImRealtimeEvent.ModInvite(textOr(info, "user_id", selfId), textOr(info, "cname", ""));
            case "34":
                return new ImRealtimeEvent.ModAccepted(textOr(info, "user_id", selfId));
            case "35":
                return new ImRealtimeEvent.ModRemoved(textOr(info, "user_id", selfId));
            case "40":
                return new ImRealtimeEvent.ModUnmuted(textOr(info, "user_id", selfId));
            case "53":
                return new ImRealtimeEvent.Follow(
                    textOr(info, "user_id", ""),
                    textOr(info, "nickname", ""),
                    textOr(info, "head_url", ""),
                    info.path("status").asInt(0));
            default:
                break;
        }

        // Live capture confirmed this is the actual path a real new_voice_visitor push takes
        // (top-level notify_type:"new_voice_visitor" string, no msg_type field at all) — the
        // payload also carries a top-level userId naming whose profile the event is about,
        // which is NOT the visitor and must not be used here. Someone visiting a voice-room
        // host's profile while browsing their room fires this same push back to the visitor
        // themselves (source:"voice_room"), with visitor_id resolving to self in that case —
        // drop it rather than surface a nonsensical "you visited your own profile".
        for (String field : new String[]{"visitor_uid", "visitor_user_id", "visitor_id"}) {
            if (root.has(field)) {
                String visitorId = textOr(root, field, "");
                if (visitorId.isEmpty()) continue;
                if (visitorId.equals(selfId)) return null;
                String nickname = textOr(root, "nickname", textOr(root, "from_nickname", ""));
                String headUrl = textOr(root, "head_url", textOr(root, "headUrl", ""));
                return new ImRealtimeEvent.ProfileVisit(visitorId, nickname, headUrl);
            }
        }
        return null;
    }

    /**
     * Live capture (F2 push, source=voice_room) revealed the real shape:
     * {@code {"userId":170553379,"notify_type":"new_voice_visitor","visitor_id":169335562,
     * "visitor_unread_count":145,"source":"voice_room"}} — {@code userId} is whose profile
     * this event is about, {@code visitor_id} is who actually did the visiting. Our own
     * connected account was {@code 169335562} in that capture, i.e. {@code visitor_id}, while
     * {@code userId} (170553379) was a voice-room host — meaning that specific push was an
     * echo of <em>us</em> visiting <em>them</em> (auto-fired by viewing their room), not
     * someone visiting our profile. The mapper previously only read {@code userId}/
     * {@code user_id} and never looked for {@code visitor_id} at all, so it always surfaced
     * these self-authored echoes as "someone visited your profile."
     * <p>
     * We only want to notify when someone ELSE visited US: that means {@code visitor_id}
     * naming someone other than the connected account. If {@code visitor_id} is absent
     * (an older/alternate payload shape), fall back to the packet header's {@code fromId} —
     * the same "who actually triggered this" signal every sibling handler above
     * (text/image/gift/introduction) already trusts — then to {@code userId}/{@code user_id}
     * as a last resort. If every source resolves to self, drop the event rather than emit a
     * nonsensical "you visited your own profile" notification.
     */
    private ImRealtimeEvent mapProfileVisit(JsonNode root, Header h) {
        String visitorId = textOr(root, "visitor_id", textOr(root, "visitor_uid", ""));
        if (visitorId.isEmpty()) {
            long headerFromId = h.fromId();
            visitorId = (headerFromId > 0 && headerFromId != selfUserId)
                ? String.valueOf(headerFromId)
                : textOr(root, "userId", textOr(root, "user_id", ""));
        }
        if (visitorId.isEmpty() || visitorId.equals(String.valueOf(selfUserId))) return null;
        String nickname = textOr(root, "nickname", textOr(root, "from_nickname", ""));
        String headUrl = textOr(root, "head_url", textOr(root, "headUrl", ""));
        return new ImRealtimeEvent.ProfileVisit(visitorId, nickname, headUrl);
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : fallback;
    }
}
