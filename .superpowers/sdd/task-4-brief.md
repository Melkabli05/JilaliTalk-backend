# Task 4: `HtImNotifyMapper` — pure JSON-to-event mapping

## Context
Tasks 1-3 done. Task 4 moves JSON→event mapping out of `HtImUpstreamConnector` into its own pure class. Logic is VERBATIM from the current connector — this is a mechanical move. The old test (`HtImUpstreamConnectorMappingTest`) will be DELETED when the new one passes.

## Files
- Create: `src/main/java/com/jilali/im/HtImNotifyMapper.java`
- Create: `src/test/java/com/jilali/im/HtImNotifyMapperTest.java`
- Delete: `src/test/java/com/jilali/im/HtImUpstreamConnectorMappingTest.java`

## Exact code

### HtImNotifyMapper.java
```java
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
```

### HtImNotifyMapperTest.java (write FIRST — test the 10 cases from the existing HtImUpstreamConnectorMappingTest):
```java
package com.jilali.im;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.im.HtImPacketFramer.Header;
import com.jilali.im.dto.ImRealtimeEvent;
import org.junit.jupiter.api.Test;

class HtImNotifyMapperTest {

    private final ObjectMapper om = new ObjectMapper();
    private final HtImNotifyMapper mapper = new HtImNotifyMapper(1L);
    private static final Header HEADER = new Header(0xF2, 1, 16386, 0, 999L, 1L, 0);

    private ImRealtimeEvent map(String json) throws Exception {
        JsonNode root = om.readTree(json);
        return mapper.map(root, HEADER);
    }

    @Test
    void notifyType18MapsToStageInvite() throws Exception {
        var e = assertInstanceOf(ImRealtimeEvent.StageInvite.class,
            map("{\"notify_type\":\"18\",\"notify_info\":{\"user_id\":\"3\",\"cname\":\"VR_1_2\"}}"));
        assertEquals("3", e.userId());
        assertEquals("VR_1_2", e.cname());
    }

    @Test
    void notifyType48MapsToModInvite() throws Exception {
        var e = assertInstanceOf(ImRealtimeEvent.ModInvite.class,
            map("{\"notify_type\":\"48\",\"notify_info\":{\"user_id\":\"11\",\"cname\":\"VR_1_2\"}}"));
        assertEquals("11", e.userId());
        assertEquals("VR_1_2", e.cname());
    }

    @Test
    void notifyType34MapsToModAccepted() throws Exception {
        assertEquals("7",
            assertInstanceOf(ImRealtimeEvent.ModAccepted.class,
                map("{\"notify_type\":\"34\",\"notify_info\":{\"user_id\":\"7\"}}")).userId());
    }

    @Test
    void notifyType35MapsToModRemoved() throws Exception {
        assertEquals("7",
            assertInstanceOf(ImRealtimeEvent.ModRemoved.class,
                map("{\"notify_type\":\"35\",\"notify_info\":{\"user_id\":\"7\"}}")).userId());
    }

    @Test
    void notifyType40MapsToModUnmuted() throws Exception {
        assertEquals("7",
            assertInstanceOf(ImRealtimeEvent.ModUnmuted.class,
                map("{\"notify_type\":\"40\",\"notify_info\":{\"user_id\":\"7\"}}")).userId());
    }

    @Test
    void notifyType53MapsToFollow() throws Exception {
        var e = assertInstanceOf(ImRealtimeEvent.Follow.class,
            map("{\"notify_type\":\"53\",\"notify_info\":{\"nickname\":\"Jilali\",\"status\":2}}"));
        assertEquals("Jilali", e.nickname());
        assertEquals(2, e.status());
    }

    @Test
    void notifyType48WithNoUserIdFallsBackToSelf() throws Exception {
        var e = assertInstanceOf(ImRealtimeEvent.ModInvite.class,
            map("{\"notify_type\":48,\"notify_info\":{\"cname\":\"VR_x\",\"host_id\":0}}"));
        assertEquals("1", e.userId());
    }

    @Test
    void newVoiceVisitorMapsToProfileVisit() throws Exception {
        assertEquals("148459398",
            assertInstanceOf(ImRealtimeEvent.ProfileVisit.class,
                map("{\"msg_type\":\"new_voice_visitor\",\"userId\":\"148459398\"}")).visitorUserId());
    }

    @Test
    void legacyVisitorUidFieldMapsToProfileVisit() throws Exception {
        assertEquals("55",
            assertInstanceOf(ImRealtimeEvent.ProfileVisit.class,
                map("{\"notify_type\":\"90\",\"visitor_uid\":\"55\"}")).visitorUserId());
    }

    @Test
    void cnameTopLevelTakesPriorityOverNotifyMapping() throws Exception {
        assertInstanceOf(ImRealtimeEvent.VoiceRoomShared.class,
            map("{\"notify_type\":\"1\",\"cname\":\"VR_1\",\"nickname\":\"H\",\"count\":3}"));
    }
}
```

## Steps
1. Write test (fails — HtImNotifyMapper doesn't exist)
2. Write class (tests pass)
3. Run full suite: `./gradlew test`
4. Delete old test: `rm src/test/java/com/jilali/im/HtImUpstreamConnectorMappingTest.java`
5. Commit

## Commit message
`feat(im): add HtImNotifyMapper, move JSON-to-event mapping out of the connector`

## Report file
`/home/mohammed/Desktop/JilaliTalk/jilalibff/.superpowers/sdd/task-4-report.md`

## Working directory
`/home/mohammed/Desktop/JilaliTalk/jilalibff`
