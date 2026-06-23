package com.jilali.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.im.dto.ImEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Parses {@code ht_im/sock}'s decrypted, decompressed push-notification JSON (the payload of
 * a {@code 0xF2} packet, after {@link com.jilali.crypto.QqTeaCipher} + optional zlib inflate)
 * into {@link ImEvent}s. Mirrors {@code com.jilali.realtime.HtNotifyMapper}'s role for the
 * room-scoped LiveHub protocol — same idea, different wire shape and account-level scope.
 *
 * <p>Field names below are taken directly from the reference client's read sites
 * (scriptv2.js's {@code onMessage} handler), not from a captured real {@code new_voice_visitor}
 * sample — there is no such capture in the reference materials, only the read site
 * ({@code obj.userId}). Everything else ({@code text}, {@code image}, {@code voice_room},
 * {@code live_link}) is read the same way the reference client reads it.
 */
@Singleton
public class HtImMessageMapper {

    private static final Logger log = LoggerFactory.getLogger(HtImMessageMapper.class);

    private final ObjectMapper om;

    public HtImMessageMapper(ObjectMapper om) {
        this.om = om;
    }

    public Optional<ImEvent> map(String json) {
        JsonNode root;
        try {
            root = om.readTree(json);
        } catch (Exception e) {
            return Optional.of(new ImEvent.Error("Malformed im frame: " + abbrev(json)));
        }
        if (root == null || root.isNull()) return Optional.empty();

        try {
            String msgType = textOr(root, "msg_type", null);
            String notifyType = textOr(root, "notify_type", null);
            String type = msgType != null ? msgType : notifyType;

            ImEvent event = switch (type) {
                case "text" -> new ImEvent.TextMessage(
                    textOr(root, "from_id", ""),
                    textOr(root.path("text"), "text", ""),
                    root.path("send_ts").asLong(System.currentTimeMillis()));
                case "image" -> new ImEvent.ImageMessage(
                    textOr(root, "from_id", ""),
                    textOr(root.path("image"), "url", ""),
                    root.path("send_ts").asLong(System.currentTimeMillis()));
                case "voice_room" -> new ImEvent.VoiceRoomShared(
                    textOr(root, "from_nickname", "Someone"),
                    textOr(root.path("voice_room"), "cname", ""),
                    textOr(root.path("voice_room"), "head_url", null),
                    1);
                case "live_link" -> new ImEvent.LiveRoomShared(
                    textOr(root, "from_nickname", textOr(root.path("live_link"), "host_name", "Someone")),
                    cnameFromLiveUrl(textOr(root.path("live_link"), "live_url", "")),
                    textOr(root.path("live_link"), "host_avatar", null));
                case "new_voice_visitor" -> new ImEvent.ProfileVisit(textOr(root, "userId", ""));
                case null, default -> null;
            };
            return Optional.ofNullable(event);
        } catch (Exception e) {
            log.warn("ht_im/sock event mapping failed: {}", e.getMessage());
            return Optional.of(new ImEvent.Error("Mapping failed: " + e.getMessage()));
        }
    }

    /** Mirrors the reference client's {@code getcnamefromurl()} — the room id is the last
     *  path segment of the share URL. */
    private static String cnameFromLiveUrl(String url) {
        if (url == null || url.isBlank()) return "";
        int idx = url.lastIndexOf('/');
        return idx >= 0 && idx < url.length() - 1 ? url.substring(idx + 1) : url;
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : fallback;
    }

    private static String abbrev(String s) {
        return s != null && s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }
}
