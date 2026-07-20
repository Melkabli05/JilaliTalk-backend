package com.jilali.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.realtime.dto.RoomCcRealtimeEvent;
import org.junit.jupiter.api.Test;

class HtCcNotifyMapperTest {

    private final ObjectMapper om = new ObjectMapper();
    private final HtCcNotifyMapper mapper = new HtCcNotifyMapper(om);

    @Test
    void subtitleStartCapturesSpeakerAndCname() {
        RoomCcRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":1,"notify_info":{
              "_id":"cc1","cname":"VR_1","nick_name":"Speaker","head_url":"https://h/s.jpg",
              "nationality":"JP","role_type":3,"user_id":"42"
            }}}
            """).orElseThrow();
        var s = assertInstanceOf(RoomCcRealtimeEvent.SubtitleStart.class, event);
        assertEquals("VR_1", s.cname());
        assertEquals("42", s.speakerId());
        assertEquals("Speaker", s.speakerNickname());
        assertEquals("https://h/s.jpg", s.speakerHeadUrl());
        assertEquals("JP", s.nationality());
        assertEquals(3, s.roleType());
        assertEquals("cc1", s.id());
    }

    @Test
    void subtitleEndCapturesCname() {
        RoomCcRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":2,"notify_info":{"cname":"VR_1"}}}
            """).orElseThrow();
        assertEquals("VR_1", assertInstanceOf(RoomCcRealtimeEvent.SubtitleEnd.class, event).cname());
    }

    @Test
    void subtitleDisabledCapturesCname() {
        RoomCcRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":3,"notify_info":{"cname":"VR_1"}}}
            """).orElseThrow();
        assertEquals("VR_1", assertInstanceOf(RoomCcRealtimeEvent.SubtitleDisabled.class, event).cname());
    }

    @Test
    void subtitleLineCapturesAllFields() {
        RoomCcRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":4,"notify_info":{
              "cname":"VR_1","_id":"line-1","text":"hello world",
              "user_id":"42","nick_name":"Speaker","head_url":"https://h/s.jpg",
              "nationality":"JP","role_type":3,
              "create_at":1750622060,"update_at":1750622061,"result_id":42,
              "enabled":true,"expired_at":1750623000
            }}}
            """).orElseThrow();
        var l = assertInstanceOf(RoomCcRealtimeEvent.SubtitleLine.class, event);
        assertEquals("VR_1", l.cname());
        assertEquals("line-1", l.id());
        assertEquals("hello world", l.text());
        assertEquals("42", l.userId());
        assertEquals("Speaker", l.nickName());
        assertEquals("https://h/s.jpg", l.headUrl());
        assertEquals("JP", l.nationality());
        assertEquals(3, l.roleType());
        assertEquals(1750622060000L, l.createAt());
        assertEquals(1750622061000L, l.updateAt());
        assertEquals(42L, l.resultId());
        assertEquals(Boolean.TRUE, l.enabled());
        assertEquals(1750623000000L, l.expiredAt());
    }

    @Test
    void subtitleExperienceActivatedCapturesUserId() {
        RoomCcRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":6,"notify_info":{"cname":"VR_1","user_id":"42"}}}
            """).orElseThrow();
        var a = assertInstanceOf(RoomCcRealtimeEvent.SubtitleExperienceActivated.class, event);
        assertEquals("VR_1", a.cname());
        assertEquals("42", a.userId());
    }

    @Test
    void subtitleExpiredConvertsSecondsToMs() {
        RoomCcRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":12,"notify_info":{"cname":"VR_1","expired_at":1750623000}}}
            """).orElseThrow();
        var e = assertInstanceOf(RoomCcRealtimeEvent.SubtitleExpired.class, event);
        assertEquals("VR_1", e.cname());
        assertEquals(1750623000000L, e.expiredAt());
    }

    @Test
    void killType5FallsThroughToRaw() {
        // type 5 is in the kill set on Android but isn't otherwise modeled; surface raw so the
        // frontend can drive the same unconditional teardown if it wants.
        RoomCcRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":5,"notify_info":{"cname":"VR_1","foo":"bar"}}}
            """).orElseThrow();
        assertEquals("5", assertInstanceOf(RoomCcRealtimeEvent.Raw.class, event).originalType());
    }

    @Test
    void roomShapedFramesAreNotRoutedToCc() {
        // A notify_type:1 frame shaped like a room event (with cname + user_id but NO
        // nick_name / role_type / etc.) must NOT be claimed by the CC mapper — the room mapper
        // owns it.
        assertTrue(mapper.map("""
            {"event":{"notify_type":"1","notify_info":{"user_id":"42","nickname":"K","head_url":"https://x","cname":"VR_1"}}}
            """).isEmpty());
    }

    @Test
    void heartbeatFramesAreNotRoutedToCc() {
        assertTrue(mapper.map("{\"heartbeat_sec\":60}").isEmpty());
        assertTrue(mapper.map("{\"heartbeat_time\":1750622060}").isEmpty());
    }

    @Test
    void malformedJsonProducesAnErrorEvent() {
        RoomCcRealtimeEvent event = mapper.map("not json").orElseThrow();
        assertInstanceOf(RoomCcRealtimeEvent.Error.class, event);
    }
}
