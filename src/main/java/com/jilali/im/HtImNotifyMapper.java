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
                case "gift"              -> mapGift(root, h);
                case "introduction"      -> mapIntro(root, h);
                case "new_voice_visitor" -> mapProfileVisit(root, h);
                default                  -> null;
            };
        }
        if (root.has("notify_type")) return mapNotify(root);
        return null;
    }

    private ImRealtimeEvent mapText(JsonNode root, Header h) {
        String fromId = textOr(root, "from_id", String.valueOf(h.fromId()));
        JsonNode t = root.path("text");
        String text = t.isObject() ? textOr(t, "text", "") : t.asText("");
        long ts = root.path("ts").asLong(System.currentTimeMillis());
        return new ImRealtimeEvent.TextMessage(fromId, text, ts);
    }

    private ImRealtimeEvent mapImage(JsonNode root, Header h) {
        String fromId = textOr(root, "from_id", String.valueOf(h.fromId()));
        String url = root.path("image").path("url").asText("");
        if (url.isBlank()) url = textOr(root, "image_url", "");
        long ts = root.path("ts").asLong(System.currentTimeMillis());
        return new ImRealtimeEvent.ImageMessage(fromId, url, ts);
    }

    private ImRealtimeEvent mapGift(JsonNode root, Header h) {
        String fromId       = textOr(root, "from_id", String.valueOf(h.fromId()));
        String fromNickname = textOr(root, "from_nickname", textOr(root, "nickname", ""));
        String fromHeadUrl  = textOr(root, "from_head_url", textOr(root, "head_url", ""));
        long   giftId       = root.path("gift_id").asLong(0);
        int    count        = root.path("gift_number").asInt(1);
        return new ImRealtimeEvent.GiftMessage(fromId, fromNickname, fromHeadUrl, giftId, count);
    }

    private ImRealtimeEvent mapIntro(JsonNode root, Header h) {
        String fromId       = textOr(root, "from_id", String.valueOf(h.fromId()));
        String fromNickname = textOr(root, "from_nickname", textOr(root, "nickname", ""));
        String fromHeadUrl  = textOr(root, "from_head_url", textOr(root, "head_url", ""));
        return new ImRealtimeEvent.IntroductionMessage(fromId, fromNickname, fromHeadUrl);
    }

    private ImRealtimeEvent mapNotify(JsonNode root) {
        if (root.has("cname")) {
            String cname        = textOr(root, "cname", "");
            String fromNickname = textOr(root, "from_nickname", textOr(root, "nickname", ""));
            String headUrl      = root.path("head_url").isNull() ? null : textOr(root, "head_url", null);
            if (root.has("count") || root.has("voice_count")) {
                int cnt = root.has("count") ? root.path("count").asInt(0) : root.path("voice_count").asInt(0);
                return new ImRealtimeEvent.VoiceRoomShared(fromNickname, cname, headUrl, cnt);
            }
            return new ImRealtimeEvent.LiveRoomShared(fromNickname, cname, headUrl);
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

        for (String field : new String[]{"visitor_uid", "visitor_user_id", "visitor_id"}) {
            if (root.has(field)) {
                String visitorId = textOr(root, field, "");
                if (!visitorId.isEmpty()) {
                    String nickname = textOr(root, "nickname", textOr(root, "from_nickname", ""));
                    String headUrl = textOr(root, "head_url", textOr(root, "headUrl", ""));
                    return new ImRealtimeEvent.ProfileVisit(visitorId, nickname, headUrl);
                }
            }
        }
        return null;
    }

    /**
     * The raw {@code new_voice_visitor} payload's own {@code userId}/{@code user_id} field
     * reflects the receiving account (us), not the actual visitor — confirmed by a live report
     * of every push showing our own uid as "who visited." Every sibling personal-message
     * handler above (text/image/gift/introduction) already treats the packet header's
     * {@code fromId} as the reliable "who actually triggered this" signal, falling back to it
     * when the JSON body lacks its own {@code from_id}; this was the one handler that never
     * received the header at all. Reversed here: prefer {@code h.fromId()} whenever it names
     * someone other than us, since the JSON body's field for this specific push type cannot be
     * trusted; fall back to the JSON body only if the header is unusable (missing/zero) or
     * also coincidentally self. If both sources resolve to self, drop the event rather than
     * emit a nonsensical "you visited your own profile" notification.
     */
    private ImRealtimeEvent mapProfileVisit(JsonNode root, Header h) {
        long headerFromId = h.fromId();
        String visitorId = (headerFromId > 0 && headerFromId != selfUserId)
            ? String.valueOf(headerFromId)
            : textOr(root, "userId", textOr(root, "user_id", ""));
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
