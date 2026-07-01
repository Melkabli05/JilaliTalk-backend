package com.jilali.im;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.im.HtImPacketFramer.Header;
import com.jilali.im.dto.ImRealtimeEvent;
import org.junit.jupiter.api.Test;

/**
 * Covers the personal ht_im/sock notify_type pushes (stage invite, mod invite, mod
 * accepted/removed/unmuted, follow, profile visit) that scriptv2.js startwebsock() received
 * on this same channel but the BFF's port had never implemented.
 */
class HtImUpstreamConnectorMappingTest {

    private final ObjectMapper om = new ObjectMapper();
    private final HtImUpstreamConnector connector =
        new HtImUpstreamConnector(1L, "jwt", "device-id", "device-model", om);
    private static final Header HEADER = new Header(0xF2, 1, 16386, 0, 999L, 1L, 0);

    private ImRealtimeEvent map(String json) throws Exception {
        JsonNode root = om.readTree(json);
        return connector.mapPushPayload(root, HEADER);
    }

    @Test
    void notifyType18MapsToStageInvite() throws Exception {
        ImRealtimeEvent event = map(
            "{\"notify_type\":\"18\",\"notify_info\":{\"user_id\":\"3\",\"cname\":\"VR_1_2\"}}");
        var invite = assertInstanceOf(ImRealtimeEvent.StageInvite.class, event);
        assertEquals("3", invite.userId());
        assertEquals("VR_1_2", invite.cname());
    }

    @Test
    void notifyType48MapsToModInvite() throws Exception {
        ImRealtimeEvent event = map(
            "{\"notify_type\":\"48\",\"notify_info\":{\"user_id\":\"11\",\"cname\":\"VR_1_2\"}}");
        var invite = assertInstanceOf(ImRealtimeEvent.ModInvite.class, event);
        assertEquals("11", invite.userId());
        assertEquals("VR_1_2", invite.cname());
    }

    @Test
    void notifyType34MapsToModAccepted() throws Exception {
        ImRealtimeEvent event = map("{\"notify_type\":\"34\",\"notify_info\":{\"user_id\":\"7\"}}");
        assertEquals("7", assertInstanceOf(ImRealtimeEvent.ModAccepted.class, event).userId());
    }

    @Test
    void notifyType35MapsToModRemoved() throws Exception {
        ImRealtimeEvent event = map("{\"notify_type\":\"35\",\"notify_info\":{\"user_id\":\"7\"}}");
        assertEquals("7", assertInstanceOf(ImRealtimeEvent.ModRemoved.class, event).userId());
    }

    @Test
    void notifyType40MapsToModUnmuted() throws Exception {
        ImRealtimeEvent event = map("{\"notify_type\":\"40\",\"notify_info\":{\"user_id\":\"7\"}}");
        assertEquals("7", assertInstanceOf(ImRealtimeEvent.ModUnmuted.class, event).userId());
    }

    @Test
    void notifyType53MapsToFollow() throws Exception {
        ImRealtimeEvent event = map(
            "{\"notify_type\":\"53\",\"notify_info\":{\"nickname\":\"Jilali\",\"status\":2}}");
        var follow = assertInstanceOf(ImRealtimeEvent.Follow.class, event);
        assertEquals("Jilali", follow.nickname());
        assertEquals(2, follow.status());
    }

    @Test
    void notifyType48WithNoUserIdFallsBackToConnectorsOwnUid() throws Exception {
        // Real capture: HelloTalk never includes user_id on this personal channel — the invite
        // is implicitly "you" — only cname and the inviting host_id are present.
        ImRealtimeEvent event = map(
            "{\"notify_type\":48,\"notify_info\":{\"cname\":\"VR_131331894_1782897947418799102\",\"host_id\":131331894}}");
        var invite = assertInstanceOf(ImRealtimeEvent.ModInvite.class, event);
        assertEquals("1", invite.userId()); // connector was constructed with userId=1L
        assertEquals("VR_131331894_1782897947418799102", invite.cname());
    }

    @Test
    void newVoiceVisitorMsgTypeMapsToProfileVisit() throws Exception {
        ImRealtimeEvent event = map("{\"msg_type\":\"new_voice_visitor\",\"userId\":\"148459398\"}");
        assertEquals("148459398", assertInstanceOf(ImRealtimeEvent.ProfileVisit.class, event).visitorUserId());
    }

    @Test
    void legacyVisitorFieldShapeStillMapsToProfileVisit() throws Exception {
        ImRealtimeEvent event = map("{\"notify_type\":\"90\",\"visitor_uid\":\"55\"}");
        assertEquals("55", assertInstanceOf(ImRealtimeEvent.ProfileVisit.class, event).visitorUserId());
    }

    @Test
    void cnameShareStillTakesPriorityOverNotifyMapping() throws Exception {
        ImRealtimeEvent event = map(
            "{\"notify_type\":\"1\",\"cname\":\"VR_1_2\",\"nickname\":\"Host\",\"count\":3}");
        assertInstanceOf(ImRealtimeEvent.VoiceRoomShared.class, event);
    }
}
