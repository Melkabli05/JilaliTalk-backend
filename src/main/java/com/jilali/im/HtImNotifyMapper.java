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
                case "new_voice_visitor" -> mapProfileVisit(root);
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
        long   giftId       = root.path("gift_id").asLong(0);
        int    count        = root.path("gift_number").asInt(1);
        return new ImRealtimeEvent.GiftMessage(fromId, fromNickname, giftId, count);
    }

    private ImRealtimeEvent mapIntro(JsonNode root, Header h) {
        String fromId       = textOr(root, "from_id", String.valueOf(h.fromId()));
        String fromNickname = textOr(root, "from_nickname", textOr(root, "nickname", ""));
        return new ImRealtimeEvent.IntroductionMessage(fromId, fromNickname);
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
                return new ImRealtimeEvent.Follow(textOr(info, "nickname", ""), info.path("status").asInt(0));
            default:
                break;
        }

        for (String field : new String[]{"visitor_uid", "visitor_user_id", "visitor_id"}) {
            if (root.has(field)) {
                return new ImRealtimeEvent.ProfileVisit(textOr(root, field, ""));
            }
        }
        return null;
    }

    private ImRealtimeEvent mapProfileVisit(JsonNode root) {
        String visitorId = textOr(root, "userId", textOr(root, "user_id", ""));
        return visitorId.isEmpty() ? null : new ImRealtimeEvent.ProfileVisit(visitorId);
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : fallback;
    }
}
