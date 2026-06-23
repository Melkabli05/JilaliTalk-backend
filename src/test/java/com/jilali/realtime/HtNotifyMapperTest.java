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
        // Shape matches a real captured LiveHub frame: gift_id/gift_number/gift_val/avatars/
        // nations live on the per-user entry in `users[]`; vip_type/gift_level/day_rank_level
        // are siblings of `users` on the outer notify_info, not duplicated per user.
        RoomRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":"1","notify_info":{"cname":"VR_1_2","type":1,
              "vip_type":3,"gift_level":19,"day_rank_level":9,"gift_id":0,"users":[
              {"send_uid":"1","send_nickname":"A","send_head_url":"https://x/a.jpg","send_nation":"JP",
               "receiver_uid":"2","receiver_nickname":"B","receiver_head_url":"https://x/b.jpg","receiver_nation":"IT",
               "small_pic":"https://x/y.png","gift_id":1120,"gift_number":1,"gift_val":1}
            ]}},"msg_id":"m2"}
            """).orElseThrow();

        var gift = assertInstanceOf(RoomRealtimeEvent.Gift.class, event);
        assertEquals(1, gift.gifts().size());
        var g = gift.gifts().get(0);
        assertEquals("A", g.sendNickname());
        assertEquals("https://x/a.jpg", g.sendHeadUrl());
        assertEquals("JP", g.sendNation());
        assertEquals("B", g.receiverNickname());
        assertEquals("https://x/b.jpg", g.receiverHeadUrl());
        assertEquals("IT", g.receiverNation());
        assertEquals(1120L, g.giftId());
        assertEquals(1, g.giftNumber());
        assertEquals(1L, g.giftVal());
        assertEquals(3, g.vipType());
        assertEquals(19, g.giftLevel());
        assertEquals(9, g.dayRankLevel());
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
        // Shape matches a real captured LiveHub frame: id/created_at/role/vip_type/bubble_*/
        // fg_*/nationality all live on notify_info directly, not nested under msg as
        // msg_id/send_ts (the previous, incorrect assumption — see mapComment()'s comment).
        RoomRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":"25","notify_info":{
              "_id":"c1","created_at":1750622060,"user_id":"131331894","nickname":"Jilali","head_url":"https://h",
              "nationality":"MA","role":3,"vip_type":2,"day_rank_level":5,"gift_level":7,
              "fg_level":3,"fg_name":"Buddies","fg_is_active":true,
              "bubble_id":4,"bubble_url":"https://bubble","bubble_color":"#abcdef",
              "hit_bad":0,"bubble_animal_type":1,"bubble_animal_url":"https://animal",
              "msg":{"text":{"text":"hello"},
                "reply_info":{"msg_id":"c0","from_id":155790171,"from_nickname":"MD","text":"orig","msg_type":"text"}}
            }},"msg_id":"m11"}
            """).orElseThrow();

        var comment = assertInstanceOf(RoomRealtimeEvent.Comment.class, event).comment();
        assertEquals("c1", comment.id());
        assertEquals(1750622060_000L, comment.ts());
        assertEquals("hello", comment.text());
        assertEquals("MD", comment.replyInfo().fromNickname());
        assertEquals("MA", comment.nationality());
        assertEquals(3, comment.role());
        assertEquals(2, comment.vipType());
        assertEquals(5, comment.dayRankLevel());
        assertEquals(7, comment.giftLevel());
        assertEquals(3, comment.fgLevel());
        assertEquals("Buddies", comment.fgName());
        assertTrue(comment.fgIsActive());
        assertEquals(4, comment.bubbleId());
        assertEquals("https://bubble", comment.bubbleUrl());
        assertEquals("#abcdef", comment.bubbleColor());
        assertEquals(0, comment.hitBad());
        assertEquals(1, comment.bubbleAnimalType());
        assertEquals("https://animal", comment.bubbleAnimalUrl());
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

    @Test
    void notifyType2FromCmd17939EnabledToggleIsDroppedInsteadOfBecomingAUserQuit() {
        // Real bug: LiveHub's `notify_type` is scoped per `cmd`. cmd 17939 carries room-wide
        // "enabled" toggles on notify_type 1 and 2 with user_id = 0 — the same type code as a
        // real quit (cmd 17923). Without the user_id != 0 guard (matching scriptv2.js:4944),
        // every toggle would produce a phantom UserQuit("0"), which the frontend then renders
        // as a duplicate "left room" card. The mapper must drop these silently.
        RoomRealtimeEvent quit = mapper.map(
            "{\"cmd\":17923,\"event\":{\"notify_type\":2,\"notify_info\":{\"cname\":\"VR_1\",\"leave_reason\":1,\"user_id\":166832448}},\"msg_id\":\"m\"}").orElseThrow();
        assertEquals("166832448", assertInstanceOf(RoomRealtimeEvent.UserQuit.class, quit).userId());

        assertTrue(mapper.map(
            "{\"cmd\":17939,\"event\":{\"notify_type\":2,\"notify_info\":{\"cname\":\"VR_1\",\"enabled\":false,\"user_id\":0}},\"msg_id\":\"m\"}").isEmpty(),
            "cmd 17939's enabled toggle (notify_type 2, user_id 0) must be dropped, not mapped to UserQuit(\"0\")");
    }
}
