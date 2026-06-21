package com.jilali.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class HtNotifyMapperTest {

    private final HtNotifyMapper mapper = new HtNotifyMapper(new ObjectMapper());

    @Test
    void heartbeatSecExtractsTheConfiguredInterval() {
        assertEquals(60L, mapper.heartbeatSec("{\"heartbeat_sec\":60}").getAsLong());
        assertTrue(mapper.heartbeatSec("{\"event\":{}}").isEmpty());
    }

    @Test
    void isHeartbeatResponseDetectsTheAckFrame() {
        assertTrue(mapper.isHeartbeatResponse("{\"heartbeat_time\":1718000000}"));
        assertFalse(mapper.isHeartbeatResponse("{\"heartbeat_sec\":60}"));
    }

    @Test
    void msgIdExtractsTheAckTarget() {
        assertEquals(Optional.of("abc-123"), mapper.msgId("{\"msg_id\":\"abc-123\",\"event\":{}}"));
        assertEquals(Optional.empty(), mapper.msgId("{\"heartbeat_sec\":60}"));
    }

    @Test
    void heartbeatAndAckFramesProduceNoRoomEvent() {
        assertTrue(mapper.map("{\"heartbeat_sec\":60}").isEmpty());
        assertTrue(mapper.map("{\"heartbeat_time\":1718000000}").isEmpty());
    }

    @Test
    void notifyType1WithoutGiftShapeMapsToUserJoin() {
        RoomRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":"1","notify_info":{"user_id":"131331894","nickname":"Jilali","is_banned_comment":false}},"msg_id":"m1"}
            """).orElseThrow();

        var join = assertInstanceOf(RoomRealtimeEvent.UserJoin.class, event);
        assertEquals("131331894", join.userId());
        assertEquals("Jilali", join.nickname());
    }

    @Test
    void notifyType1WithGiftShapeMapsToGiftAndDisambiguatesFromJoin() {
        RoomRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":"1","notify_info":{"cname":"VR_1_2","type":1,"users":[
              {"send_uid":"1","send_nickname":"A","receiver_uid":"2","receiver_nickname":"B","small_pic":"https://x/y.png"}
            ]}},"msg_id":"m2"}
            """).orElseThrow();

        var gift = assertInstanceOf(RoomRealtimeEvent.Gift.class, event);
        assertEquals(1, gift.gifts().size());
        assertEquals("A", gift.gifts().get(0).sendNickname());
        assertEquals("B", gift.gifts().get(0).receiverNickname());
    }

    @Test
    void notifyType2MapsToUserQuit() {
        RoomRealtimeEvent event = mapper.map(
            "{\"event\":{\"notify_type\":\"2\",\"notify_info\":{\"user_id\":\"42\"}},\"msg_id\":\"m3\"}").orElseThrow();
        assertEquals("42", assertInstanceOf(RoomRealtimeEvent.UserQuit.class, event).userId());
    }

    @Test
    void notifyType3WithKickType2MapsToUserQuit() {
        // kick_type 1 (mapped to RoomKick) carries manager_name; kick_type 2 never does —
        // it's a voluntary leave, the same real-world event as notify_type 2.
        RoomRealtimeEvent event = mapper.map(
            "{\"event\":{\"notify_type\":\"3\",\"notify_info\":{\"audience_total\":17,"
                + "\"cname\":\"VR_149521626_1781921105276383831\",\"kick_type\":2,"
                + "\"nickname\":\"Wpsiw\",\"user_id\":171266284}},\"msg_id\":\"m14\"}").orElseThrow();
        assertEquals("171266284", assertInstanceOf(RoomRealtimeEvent.UserQuit.class, event).userId());
    }

    @Test
    void notifyType3WithKickType1MapsToRoomKick() {
        RoomRealtimeEvent event = mapper.map(
            "{\"event\":{\"notify_type\":\"3\",\"notify_info\":{\"kick_type\":1,\"user_id\":\"5\","
                + "\"nickname\":\"Target\",\"manager_name\":\"Mod\",\"cname\":\"VR_1\"}},\"msg_id\":\"m15\"}")
            .orElseThrow();
        RoomRealtimeEvent.RoomKick kick = assertInstanceOf(RoomRealtimeEvent.RoomKick.class, event);
        assertEquals("5", kick.userId());
        assertEquals("Mod", kick.managerName());
    }

    @Test
    void notifyType4And23BothMapToStageJoin() {
        RoomRealtimeEvent four = mapper.map(
            "{\"event\":{\"notify_type\":\"4\",\"notify_info\":{\"user_id\":\"7\",\"nickname\":\"KM\",\"head_url\":\"https://x\"}},\"msg_id\":\"m4\"}").orElseThrow();
        RoomRealtimeEvent twentyThree = mapper.map(
            "{\"event\":{\"notify_type\":\"23\",\"notify_info\":{\"user_id\":\"8\",\"nickname\":\"D\",\"head_url\":\"https://y\"}},\"msg_id\":\"m5\"}").orElseThrow();

        assertEquals("7", assertInstanceOf(RoomRealtimeEvent.StageJoin.class, four).stageUser().userId());
        assertEquals("8", assertInstanceOf(RoomRealtimeEvent.StageJoin.class, twentyThree).stageUser().userId());
    }

    @Test
    void notifyType5WithoutCoinMapsToStageQuitButWithCoinIsIgnored() {
        RoomRealtimeEvent quit = mapper.map(
            "{\"event\":{\"notify_type\":\"5\",\"notify_info\":{\"user_id\":\"9\"}},\"msg_id\":\"m6\"}").orElseThrow();
        assertEquals("9", assertInstanceOf(RoomRealtimeEvent.StageQuit.class, quit).userId());

        assertTrue(mapper.map(
            "{\"event\":{\"notify_type\":\"5\",\"notify_info\":{\"user_id\":\"9\",\"coin\":3}},\"msg_id\":\"m7\"}").isEmpty(),
            "notify_type 5 with a coin field is a goodie-bag result, not modeled — must not become a stage_quit");
    }

    @Test
    void notifyType10And11MapToRaiseHandWithDifferentRaisehandType() {
        RoomRealtimeEvent raised = mapper.map(
            "{\"event\":{\"notify_type\":\"10\",\"notify_info\":{\"user_id\":\"5\"}},\"msg_id\":\"m8\"}").orElseThrow();
        RoomRealtimeEvent cancelled = mapper.map(
            "{\"event\":{\"notify_type\":\"11\",\"notify_info\":{\"user_id\":\"5\"}},\"msg_id\":\"m9\"}").orElseThrow();

        assertEquals(1, assertInstanceOf(RoomRealtimeEvent.StageRaiseHand.class, raised).raisehandType());
        assertEquals(2, assertInstanceOf(RoomRealtimeEvent.StageRaiseHand.class, cancelled).raisehandType());
    }

    @Test
    void notifyType18MapsToStageInvite() {
        RoomRealtimeEvent event = mapper.map(
            "{\"event\":{\"notify_type\":\"18\",\"notify_info\":{\"user_id\":\"3\"}},\"msg_id\":\"m10\"}").orElseThrow();
        assertEquals("3", assertInstanceOf(RoomRealtimeEvent.StageInvite.class, event).userId());
    }

    @Test
    void notifyType25MapsToCommentIncludingReplyInfo() {
        RoomRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":"25","notify_info":{
              "user_id":"131331894","nickname":"Jilali","head_url":"https://h",
              "msg":{"msg_id":"c1","send_ts":1750622060441,"text":{"text":"hello"},
                "reply_info":{"msg_id":"c0","from_id":155790171,"from_nickname":"MD","text":"orig","msg_type":"text"}}
            }},"msg_id":"m11"}
            """).orElseThrow();

        var comment = assertInstanceOf(RoomRealtimeEvent.Comment.class, event).comment();
        assertEquals("hello", comment.text());
        assertEquals("MD", comment.replyInfo().fromNickname());
    }

    @Test
    void notifyType30MapsToStageDeviceControlMicMute() {
        RoomRealtimeEvent event = mapper.map(
            "{\"event\":{\"notify_type\":\"30\",\"notify_info\":{\"user_id\":\"6\"}},\"msg_id\":\"m12\"}").orElseThrow();
        var control = assertInstanceOf(RoomRealtimeEvent.StageDeviceControl.class, event);
        assertEquals(1, control.deviceType());
        assertEquals(1, control.switchType());
    }

    @Test
    void notifyType48MapsToModInvite() {
        RoomRealtimeEvent event = mapper.map(
            "{\"event\":{\"notify_type\":\"48\",\"notify_info\":{\"user_id\":\"11\"}},\"msg_id\":\"m13\"}").orElseThrow();
        assertEquals("11", assertInstanceOf(RoomRealtimeEvent.ModInvite.class, event).userId());
    }

    @Test
    void unrecognizedNotifyTypeFallsThroughToRawInsteadOfBeingDropped() {
        RoomRealtimeEvent event = mapper.map(
            "{\"event\":{\"notify_type\":\"999\",\"notify_info\":{\"foo\":\"bar\"}},\"msg_id\":\"m14\"}").orElseThrow();
        assertEquals("999", assertInstanceOf(RoomRealtimeEvent.Raw.class, event).originalType());
    }

    @Test
    void malformedJsonProducesAnErrorEventInsteadOfThrowing() {
        RoomRealtimeEvent event = mapper.map("not json").orElseThrow();
        assertInstanceOf(RoomRealtimeEvent.Error.class, event);
    }
}
