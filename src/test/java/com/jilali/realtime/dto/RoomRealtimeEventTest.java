package com.jilali.realtime.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RoomRealtimeEventTest {
    private final ObjectMapper om = new ObjectMapper();

    @Test
    void commentRoundTripsThroughJackson() throws Exception {
        var event = new RoomRealtimeEvent.Comment(
            new RoomRealtimeEvent.CommentEvent(
                "msg-1", "msg-1-srv", "131331894", "Jilali", "https://cdn/h.jpg", "hello",
                1750622060441L,
                1750622065000L,
                1750622064000L,
                "v2", "app", 0,
                new RoomRealtimeEvent.ReplyInfoEvent("msg-0", 155790171L, "MD", "original text", "text", 1750622050000L),
                "MA", 3, 2, 5, 7, 3, "Buddies", true,
                4, "https://cdn/bubble.png", "#abcdef", 0, 1, "https://cdn/animal.png"));
        String json = om.writeValueAsString(event);
        assertTrue(json.contains("\"type\":\"comment\""), json);
        assertTrue(json.contains("\"text\":\"hello\""), json);
        RoomRealtimeEvent roundTripped = om.readValue(json, RoomRealtimeEvent.class);
        assertEquals(event, roundTripped);
    }

    @Test
    void giftRoundTripsWithTypeGift() throws Exception {
        var event = new RoomRealtimeEvent.Gift(List.of(
            new RoomRealtimeEvent.GiftEvent(
                "1", "Sender", "https://cdn/sender.jpg", "JP",
                "2", "Receiver", "https://cdn/receiver.jpg", "IT",
                "https://cdn/gift.png", 1120L, 1, 1L, 3, 19, 9)));
        String json = om.writeValueAsString(event);
        assertTrue(json.contains("\"type\":\"gift\""), json);
        RoomRealtimeEvent roundTripped = om.readValue(json, RoomRealtimeEvent.class);
        assertEquals(event, roundTripped);
    }

    @Test
    void rawRoundTripsWithTypeRaw() throws Exception {
        var event = new RoomRealtimeEvent.Raw("99", java.util.Map.of("foo", "bar"));
        String json = om.writeValueAsString(event);
        assertTrue(json.contains("\"type\":\"raw\""), json);
        RoomRealtimeEvent roundTripped = om.readValue(json, RoomRealtimeEvent.class);
        assertEquals(event, roundTripped);
    }

    @Test
    void topicShareRoundTripsWithTypeRoomTopicShare() throws Exception {
        var event = new RoomRealtimeEvent.RoomTopicShare("VR_1", 1053L, 2056L, "English learning");
        String json = om.writeValueAsString(event);
        assertTrue(json.contains("\"type\":\"room_topic_share\""), json);
        assertEquals(event, om.readValue(json, RoomRealtimeEvent.class));
    }

    @Test
    void propsAppliedRoundTripsWithTypeRoomPropsApplied() throws Exception {
        var event = new RoomRealtimeEvent.RoomPropsApplied(
            "VR_1", "164164146", 460L, 7, 3,
            "https://cdn/a.png", "https://cdn/l.png", "https://cdn/b.png",
            "https://cdn/s.png", "https://cdn/t.png", 169);
        String json = om.writeValueAsString(event);
        assertTrue(json.contains("\"type\":\"room_props_applied\""), json);
        assertEquals(event, om.readValue(json, RoomRealtimeEvent.class));
    }

    @Test
    void fgUpgradeAwardEmitsAwardTypeNotType() throws Exception {
        // The FgUpgradeAward record's `type` field is renamed to `awardType` on the wire to avoid
        // colliding with the union's `type` discriminator — verify the rename actually took effect.
        var event = new RoomRealtimeEvent.FgUpgradeAward(42L, "fg", "https://i/c.png", "Level 5!");
        String json = om.writeValueAsString(event);
        assertTrue(json.contains("\"awardType\":\"fg\""), json);
        assertFalse(json.contains("\"type\":\"fg\""), json);
        assertTrue(json.contains("\"type\":\"fg_upgrade_award\""), json);
    }
}
