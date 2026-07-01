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
