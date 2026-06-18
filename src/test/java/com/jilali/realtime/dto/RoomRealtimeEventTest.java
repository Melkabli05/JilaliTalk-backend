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
                "msg-1", "131331894", "Jilali", "https://cdn/h.jpg", "hello",
                1750622060441L,
                new RoomRealtimeEvent.ReplyInfoEvent("msg-0", 155790171L, "MD", "original text", "text")));
        String json = om.writeValueAsString(event);
        assertTrue(json.contains("\"type\":\"comment\""), json);
        assertTrue(json.contains("\"text\":\"hello\""), json);
        RoomRealtimeEvent roundTripped = om.readValue(json, RoomRealtimeEvent.class);
        assertEquals(event, roundTripped);
    }

    @Test
    void giftRoundTripsWithTypeGift() throws Exception {
        var event = new RoomRealtimeEvent.Gift(List.of(
            new RoomRealtimeEvent.GiftEvent("1", "Sender", "2", "Receiver", "https://cdn/gift.png")));
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
}
