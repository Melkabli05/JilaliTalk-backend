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
        assertEquals("", e.userId());
        assertEquals("", e.headUrl());
    }

    @Test
    void notifyType53MapsToFollowWithUserIdAndHeadUrl() throws Exception {
        var e = assertInstanceOf(ImRealtimeEvent.Follow.class,
            map("{\"notify_type\":\"53\",\"notify_info\":{\"user_id\":\"9\",\"nickname\":\"Jilali\",\"head_url\":\"https://x/a.jpg\",\"status\":1}}"));
        assertEquals("9", e.userId());
        assertEquals("Jilali", e.nickname());
        assertEquals("https://x/a.jpg", e.headUrl());
        assertEquals(1, e.status());
    }

    @Test
    void notifyType48WithNoUserIdFallsBackToSelf() throws Exception {
        var e = assertInstanceOf(ImRealtimeEvent.ModInvite.class,
            map("{\"notify_type\":48,\"notify_info\":{\"cname\":\"VR_x\",\"host_id\":0}}"));
        assertEquals("1", e.userId());
    }

    @Test
    void newVoiceVisitorPrefersHeaderFromIdOverJsonBodyUserId() throws Exception {
        // The JSON body's own userId (148459398) reflects the receiving account in real
        // traffic, not the visitor — the packet header's fromId (999, per the shared HEADER
        // fixture) is the reliable source, same convention as mapText/mapImage/mapGift/mapIntro.
        var e = assertInstanceOf(ImRealtimeEvent.ProfileVisit.class,
            map("{\"msg_type\":\"new_voice_visitor\",\"userId\":\"148459398\"}"));
        assertEquals("999", e.visitorUserId());
        assertEquals("", e.nickname());
        assertEquals("", e.headUrl());
    }

    @Test
    void newVoiceVisitorWithNicknameAndHeadUrlStillPrefersHeaderFromIdForVisitorId() throws Exception {
        var e = assertInstanceOf(ImRealtimeEvent.ProfileVisit.class,
            map("{\"msg_type\":\"new_voice_visitor\",\"userId\":\"148459398\",\"nickname\":\"Jilali\",\"head_url\":\"https://x/a.jpg\"}"));
        assertEquals("999", e.visitorUserId());
        assertEquals("Jilali", e.nickname());
        assertEquals("https://x/a.jpg", e.headUrl());
    }

    @Test
    void newVoiceVisitorFallsBackToJsonBodyWhenHeaderFromIdIsSelf() throws Exception {
        // Header fromId is unusable here (0) — should still fall back to the JSON body's userId.
        var mapperWithZeroFromIdHeader = new HtImNotifyMapper(1L);
        JsonNode root = om.readTree("{\"msg_type\":\"new_voice_visitor\",\"userId\":\"148459398\"}");
        Header zeroFromIdHeader = new Header(0xF2, 1, 16386, 0, 0L, 1L, 0);
        var e = assertInstanceOf(ImRealtimeEvent.ProfileVisit.class,
            mapperWithZeroFromIdHeader.map(root, zeroFromIdHeader));
        assertEquals("148459398", e.visitorUserId());
    }

    @Test
    void newVoiceVisitorDropsEventWhenBothSourcesResolveToSelf() throws Exception {
        // Header fromId is self (1) and the JSON body's userId is also self — a nonsensical
        // "you visited your own profile" push should be dropped, not emitted.
        var mapperSelfIs1 = new HtImNotifyMapper(1L);
        JsonNode root = om.readTree("{\"msg_type\":\"new_voice_visitor\",\"userId\":\"1\"}");
        Header selfFromIdHeader = new Header(0xF2, 1, 16386, 0, 1L, 1L, 0);
        assertEquals(null, mapperSelfIs1.map(root, selfFromIdHeader));
    }

    @Test
    void newVoiceVisitorPrefersVisitorIdFieldOverUserId() throws Exception {
        // Real shape (from live capture): userId is whose profile the event is about,
        // visitor_id is who actually did the visiting. Here someone else (170553379) visited
        // our (169335562) profile, so visitor_id should be surfaced as the visitor.
        var mapperSelfIs169335562 = new HtImNotifyMapper(169335562L);
        JsonNode root = om.readTree(
            "{\"userId\":169335562,\"notify_type\":\"new_voice_visitor\",\"visitor_id\":170553379,"
                + "\"visitor_unread_count\":145,\"source\":\"voice_room\"}");
        var e = assertInstanceOf(ImRealtimeEvent.ProfileVisit.class, mapperSelfIs169335562.map(root, HEADER));
        assertEquals("170553379", e.visitorUserId());
    }

    @Test
    void newVoiceVisitorDropsSelfAuthoredVisitEcho() throws Exception {
        // The exact live capture: our own connected account (169335562) is visitor_id — this
        // represents US visiting the room host's (170553379) profile (auto-fired by viewing
        // his voice room, source=voice_room), not someone visiting OUR profile. Must be dropped,
        // not surfaced as "someone visited your profile".
        var mapperSelfIs169335562 = new HtImNotifyMapper(169335562L);
        JsonNode root = om.readTree(
            "{\"userId\":170553379,\"notify_type\":\"new_voice_visitor\",\"visitor_id\":169335562,"
                + "\"visitor_unread_count\":145,\"source\":\"voice_room\"}");
        assertEquals(null, mapperSelfIs169335562.map(root, HEADER));
    }

    @Test
    void legacyVisitorUidFieldMapsToProfileVisit() throws Exception {
        var e = assertInstanceOf(ImRealtimeEvent.ProfileVisit.class,
            map("{\"notify_type\":\"90\",\"visitor_uid\":\"55\"}"));
        assertEquals("55", e.visitorUserId());
        assertEquals("", e.nickname());
        assertEquals("", e.headUrl());
    }

    @Test
    void cnameTopLevelTakesPriorityOverNotifyMapping() throws Exception {
        assertInstanceOf(ImRealtimeEvent.VoiceRoomShared.class,
            map("{\"notify_type\":\"1\",\"cname\":\"VR_1\",\"nickname\":\"H\",\"count\":3}"));
    }
}
