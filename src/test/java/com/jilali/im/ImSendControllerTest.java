package com.jilali.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.im.ImSendController.GiftRequest;
import com.jilali.im.ImSendController.IntroductionRequest;
import com.jilali.im.ImSendController.SendMessageRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ImSendControllerTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void textMessageHasSourceAndMsEpoch() throws Exception {
        var req = new SendMessageRequest("text", "m1", 1L, "Alice", 100L,
            "hi", null, null, null, null, null, null, null, null, null);
        JsonNode root = om.readTree(ImSendController.buildSendMessageJson(req, 2L));

        JsonNode msg = root.path("msg");
        assertEquals("text", msg.path("msg_type").asText());
        assertEquals("hi", msg.path("text").path("text").asText());
        assertEquals("Chat List", msg.path("source").asText());
        assertTrue(msg.path("send_ts").asLong() > 1_000_000_000_000L, "send_ts must be ms epoch, not seconds");
        assertEquals(394024, root.path("version").asInt());
        assertEquals("English", root.path("client_lang").asText());
    }

    @Test
    void voiceRoomAndLiveLinkOmitSource() throws Exception {
        var req = new SendMessageRequest("voice_room", null, 1L, "Alice", null,
            null, null, null, null, null, null, null, Map.of("cname", "VR_1"), null, null);
        JsonNode msg = om.readTree(ImSendController.buildSendMessageJson(req, 2L)).path("msg");

        assertEquals("VR_1", msg.path("voice_room").path("cname").asText());
        assertFalse(msg.has("source"), "voice_room must not carry a source field");
    }

    @Test
    void introductionBuildsStructuredFieldsAndBubble() throws Exception {
        var intro = new IntroductionRequest(42L, "Bob", "http://x/y.png", "m", 30, "MA", "hi there");
        var req = new SendMessageRequest("introduction", null, 1L, "Alice", null,
            null, null, null, null, null, null, null, null, intro, null);
        JsonNode msg = om.readTree(ImSendController.buildSendMessageJson(req, 2L)).path("msg");

        JsonNode i = msg.path("introduction");
        assertEquals(42, i.path("user_id").asInt());
        assertEquals("Bob", i.path("nickname").asText());
        assertEquals("MA", i.path("nationality").asText());
        assertEquals(0, msg.path("bubble").path("id").asInt());
        assertFalse(msg.has("source"), "introduction must not carry a source field");
    }

    @Test
    void sendGiftIsItsOwnTopLevelEnvelope() throws Exception {
        var gift = new GiftRequest(7L, "Rose", Map.of("en", "Rose"), "small.png", "big.png", "anim.mp4", 100L, 1);
        var req = new SendMessageRequest("send_gift", "m9", 1L, "Alice", null,
            null, null, null, null, null, null, null, null, null, gift);
        JsonNode root = om.readTree(ImSendController.buildSendMessageJson(req, 2L));

        assertFalse(root.has("msg"), "send_gift must not be nested under msg");
        assertFalse(root.has("version"), "send_gift envelope carries no version field");
        assertEquals("send_gift", root.path("msg_type").asText());
        assertEquals("1", root.path("from_id").asText());
        assertEquals(2, root.path("to_id").asInt());
        assertEquals("m9", root.path("msg_id").asText());
        assertEquals("Chat List", root.path("source").asText());

        JsonNode g = root.path("send_gift");
        assertEquals(7, g.path("id").asInt());
        assertEquals(100, g.path("diamond_val").asInt());
        assertEquals(2, g.path("users").get(0).path("user_id").asInt());
        assertEquals(1, g.path("user_size").asInt());
        assertTrue(g.path("have_birthday_user").asBoolean());
        assertFalse(g.path("finish_wish").asBoolean());
    }
}
