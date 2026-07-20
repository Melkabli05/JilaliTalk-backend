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

    private final ObjectMapper om = new ObjectMapper();
    private final HtNotifyMapper mapper = new HtNotifyMapper(om);

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
        assertEquals(null, join.headUrl());
        assertEquals(null, join.nationality());
    }

    @Test
    void notifyType1WithHeadUrlAndNationalityMapsToEnrichedUserJoin() {
        RoomRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":"1","notify_info":{"user_id":"7","nickname":"KM","head_url":"https://x","nationality":"MA","is_banned_comment":false}},"msg_id":"m1b"}
            """).orElseThrow();

        var join = assertInstanceOf(RoomRealtimeEvent.UserJoin.class, event);
        assertEquals("https://x", join.headUrl());
        assertEquals("MA", join.nationality());
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
    void notifyType4WithLuckyBagIdMapsToLuckyBagInsteadOfStageJoin() {
        RoomRealtimeEvent event = mapper.map(
            "{\"event\":{\"notify_type\":\"4\",\"notify_info\":{\"lucky_bag_id\":\"lb_1\",\"lucky_bag_number\":3,\"cname\":\"VR_1\"}},\"msg_id\":\"m6b\"}").orElseThrow();
        var bag = assertInstanceOf(RoomRealtimeEvent.LuckyBag.class, event);
        assertEquals("lb_1", bag.luckyBagId());
        assertEquals(3, bag.luckyBagNumber());
        assertEquals("VR_1", bag.cname());
    }

    @Test
    void notifyType6WithLuckyBagIdMapsToLuckyBag() {
        RoomRealtimeEvent event = mapper.map(
            "{\"event\":{\"notify_type\":\"6\",\"notify_info\":{\"lucky_bag_id\":\"lb_2\",\"lucky_bag_number\":5,\"cname\":\"VR_2\"}},\"msg_id\":\"m6c\"}").orElseThrow();
        var bag = assertInstanceOf(RoomRealtimeEvent.LuckyBag.class, event);
        assertEquals("lb_2", bag.luckyBagId());
        assertEquals(5, bag.luckyBagNumber());
    }

    @Test
    void notifyType3WithLuckyBagIdMapsToLuckyBagInsteadOfRaw() {
        RoomRealtimeEvent event = mapper.map(
            "{\"event\":{\"notify_type\":\"3\",\"notify_info\":{\"lucky_bag_id\":\"lb_3\",\"lucky_bag_number\":1,\"cname\":\"VR_3\"}},\"msg_id\":\"m6d\"}").orElseThrow();
        var bag = assertInstanceOf(RoomRealtimeEvent.LuckyBag.class, event);
        assertEquals("lb_3", bag.luckyBagId());
    }

    @Test
    void notifyType5WithSeatIdZeroMapsToStageQuitButOtherwiseIsIgnored() {
        // seat_id: 0 is the confirmed real shape of a stage-quit push (startwebsock(),
        // scriptv2.js:4908-4931 embeds a captured example with exactly this field).
        RoomRealtimeEvent quit = mapper.map(
            "{\"event\":{\"notify_type\":\"5\",\"notify_info\":{\"user_id\":\"9\",\"seat_id\":0}},\"msg_id\":\"m6\"}").orElseThrow();
        assertEquals("9", assertInstanceOf(RoomRealtimeEvent.StageQuit.class, quit).userId());

        assertTrue(mapper.map(
            "{\"event\":{\"notify_type\":\"5\",\"notify_info\":{\"user_id\":\"9\",\"coin\":3}},\"msg_id\":\"m7\"}").isEmpty(),
            "notify_type 5 with a coin field (no seat_id) is a goodie-bag result, not modeled — must not become a stage_quit");
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
    void notifyType53MapsToFollowWithUserIdAndHeadUrl() {
        RoomRealtimeEvent event = mapper.map(
            "{\"event\":{\"notify_type\":\"53\",\"notify_info\":{\"user_id\":\"9\",\"nickname\":\"Jilali\",\"head_url\":\"https://x/a.jpg\",\"status\":2}},\"msg_id\":\"m16\"}").orElseThrow();
        var follow = assertInstanceOf(RoomRealtimeEvent.Follow.class, event);
        assertEquals("9", follow.userId());
        assertEquals("Jilali", follow.nickname());
        assertEquals("https://x/a.jpg", follow.headUrl());
        assertEquals(2, follow.status());
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

    @Test
    void notifyType7MapsToRoomPropsApplied() {
        // Confirmed live-captured shape (cmd 17925, originalType 7): the props/bubble skin
        // applied in the room carries the full set of bubble URLs + paid-tier marker.
        RoomRealtimeEvent event = mapper.map("""
            {"cmd":17925,"event":{"notify_type":7,"notify_info":{
              "animal_type":3,
              "animal_url_v2":"https://ali-global-cdn.hellotalk8.com/prod/voice/bubble/7e199f84ea5ecf9e7a6ad35db9a9df06.png",
              "background_paid":169,
              "cname":"VR_162823971_1784568624338907230",
              "list_background_url":"https://ali-global-cdn.hellotalk8.com/prod/voice/bubble/7e199f84ea5ecf9e7a6ad35db9a9df06.png",
              "props_id":460,
              "props_type":7,
              "room_big_background_url":"https://ali-global-cdn.hellotalk8.com/prod/voice/bubble/preview/a4a7f51db37f610a9cd68bc9eee03f4b.png",
              "sound_wave_url":"https://ali-global-cdn.hellotalk8.com/prod/voice/bubble/7e199f84ea5ecf9e7a6ad35db9a9df06.png",
              "top_list_background_url":"https://ali-global-cdn.hellotalk8.com/prod/voice/bubble/preview/277c5e9826a8f5a6f870defe82d96d04.png",
              "user_id":164164146
            }},"from":164164146,"msg_id":"678857:7602:0"}
            """).orElseThrow();

        var props = assertInstanceOf(RoomRealtimeEvent.RoomPropsApplied.class, event);
        assertEquals("VR_162823971_1784568624338907230", props.cname());
        assertEquals("164164146", props.userId());
        assertEquals(460L, props.propsId());
        assertEquals(7, props.propsType());
        assertEquals(3, props.animalType());
        assertEquals(169, props.backgroundPaid());
        assertEquals("https://ali-global-cdn.hellotalk8.com/prod/voice/bubble/7e199f84ea5ecf9e7a6ad35db9a9df06.png", props.animalUrlV2());
        assertEquals("https://ali-global-cdn.hellotalk8.com/prod/voice/bubble/preview/a4a7f51db37f610a9cd68bc9eee03f4b.png", props.roomBigBackgroundUrl());
        assertEquals("https://ali-global-cdn.hellotalk8.com/prod/voice/bubble/preview/277c5e9826a8f5a6f870defe82d96d04.png", props.topListBackgroundUrl());
    }

    @Test
    void notifyType47MapsToRoomTopicShare() {
        // Confirmed live-captured shape (cmd 17923, originalType 47): a topic/category card
        // dropped into the room — here, an "English learning" topic in category 1053.
        RoomRealtimeEvent event = mapper.map("""
            {"cmd":17923,"event":{"notify_type":47,"notify_info":{
              "category_id":1053,
              "cname":"VR_162823971_1784568624338907230",
              "name":"English learning ",
              "topic_id":2056
            }},"from":108760518,"msg_id":"678857:7617:0"}
            """).orElseThrow();

        var share = assertInstanceOf(RoomRealtimeEvent.RoomTopicShare.class, event);
        assertEquals("VR_162823971_1784568624338907230", share.cname());
        assertEquals(1053L, share.categoryId());
        assertEquals(2056L, share.topicId());
        assertEquals("English learning ", share.name());
    }

    @Test
    void notifyType28And50FallThroughToRawPreservingPayload() {
        // Android BaseLiveFragment.onRoomUserMessageEvent switches on these but startwebsock()
        // does not — we don't yet have a captured frame for either so they pass through to Raw
        // (with the original notify_type string preserved) instead of being silently dropped.
        RoomRealtimeEvent t28 = mapper.map(
            "{\"event\":{\"notify_type\":\"28\",\"notify_info\":{\"foo\":\"bar\"}},\"msg_id\":\"m28\"}").orElseThrow();
        assertEquals("28", assertInstanceOf(RoomRealtimeEvent.Raw.class, t28).originalType());

        RoomRealtimeEvent t50 = mapper.map(
            "{\"event\":{\"notify_type\":\"50\",\"notify_info\":{\"foo\":\"bar\"}},\"msg_id\":\"m50\"}").orElseThrow();
        assertEquals("50", assertInstanceOf(RoomRealtimeEvent.Raw.class, t50).originalType());
    }

    @Test
    void stageJoinCarriesFullStageUserFieldsNotJustUserIdNicknameHeadUrl() {
        // The Android LiveWSSRoomUser payload carries ~80 fields on a notify_type 4 / 23 push;
        // before this extension the mapper only forwarded user_id/nickname/head_url, so the
        // frontend had no role / seat / VIP / bubble / enter-effect to render from the realtime
        // push — it had to wait for a REST roster pull to repaint them.
        RoomRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":"4","notify_info":{
              "user_id":"7","nickname":"KM","head_url":"https://x/h.jpg","nationality":"MA",
              "role":2,"seat_id":3,"vip_type":3,"day_rank_level":9,"gift_level":11,"g_type":1,
              "screen_share_uid":0,
              "enter_effect_id":42,"enter_effect_animal_type":2,"enter_effect_animal_url":"https://e/au.png","enter_effect_paid":1,
              "ripple_animal_type":1,"ripple_animal_url":"https://r/au.png","ripple_url":"https://r/full.png",
              "is_on_stage":true,"is_first_join":false,"is_in_room":true,
              "is_turn_on_mic":true,"is_turn_on_cam":false,
              "invite_user_id":"42","invite_nickname":"Host","invite_head_url":"https://h/host.jpg","invite_nationality":"JP",
              "bubble_id":4,"bubble_url":"https://b/bubble","bubble_color":"#abcdef","bubble_animal_type":1,"bubble_animal_url":"https://b/animal",
              "fg_level":3,"fg_name":"Buddies","fg_is_active":true,
              "follower_id":155,"followee_id":156,"audience_total":17,"raise_hand_count":0,
              "medal_wall_icon":"https://m/icon.png",
              "joinTime":1750622060000,"created_at":1750622060,
              "pinned_status":1,"pinned_type":"sticky","team_index":2,
              "status":0,"type":0,"name":null,"label":null,"level":0,
              "reason":null,"notice":null,"tip_text":null,"share_status":null,"location":null
            }},"msg_id":"m_enrich"}
            """).orElseThrow();

        var su = assertInstanceOf(RoomRealtimeEvent.StageJoin.class, event).stageUser();
        assertEquals("7", su.userId());
        assertEquals("KM", su.nickname());
        assertEquals("https://x/h.jpg", su.headUrl());
        assertEquals("MA", su.nationality());
        assertEquals(2, su.role());
        assertEquals(3, su.seatId());
        assertEquals(3, su.vipType());
        assertEquals(9, su.dayRankLevel());
        assertEquals(11, su.giftLevel());
        assertEquals(1, su.gType());
        assertEquals(42, su.enterEffectId());
        assertEquals(2, su.enterEffectAnimalType());
        assertEquals("https://e/au.png", su.enterEffectAnimalUrl());
        assertEquals(1, su.enterEffectPaid());
        assertEquals(1, su.rippleAnimalType());
        assertEquals("https://r/au.png", su.rippleAnimalUrl());
        assertEquals("https://r/full.png", su.rippleUrl());
        assertTrue(su.isOnStage());
        assertFalse(su.isFirstJoin());
        assertTrue(su.isInRoom());
        assertTrue(su.isTurnOnMic());
        assertFalse(su.isTurnOnCam());
        assertEquals("42", su.inviteUserId());
        assertEquals("Host", su.inviteNickname());
        assertEquals("https://h/host.jpg", su.inviteHeadUrl());
        assertEquals("JP", su.inviteNationality());
        assertEquals(4, su.bubbleId());
        assertEquals("https://b/bubble", su.bubbleUrl());
        assertEquals("#abcdef", su.bubbleColor());
        assertEquals(1, su.bubbleAnimalType());
        assertEquals("https://b/animal", su.bubbleAnimalUrl());
        assertEquals(3, su.fgLevel());
        assertEquals("Buddies", su.fgName());
        assertTrue(su.fgIsActive());
        assertEquals(155L, su.followerId());
        assertEquals(156L, su.followeeId());
        assertEquals(17, su.audienceTotal());
        assertEquals(0, su.raiseHandCount());
        assertEquals("https://m/icon.png", su.medalWallIcon());
        assertEquals(1750622060000L, su.joinTime());
        assertEquals(1750622060000L, su.createdAt());
        assertEquals(1, su.pinnedStatus());
        assertEquals("sticky", su.pinnedType());
        assertEquals(2, su.teamIndex());
    }

    @Test
    void stageJoinWithMinimalInfoFillsUnknownsToDefaults() {
        // Stage-join with only user_id/nickname should still parse without NPE — everything
        // else falls back to the zero-equivalent default (null / 0 / false).
        RoomRealtimeEvent event = mapper.map(
            "{\"event\":{\"notify_type\":\"4\",\"notify_info\":{\"user_id\":\"7\",\"nickname\":\"KM\"}},\"msg_id\":\"m_min\"}").orElseThrow();
        var su = assertInstanceOf(RoomRealtimeEvent.StageJoin.class, event).stageUser();
        assertEquals("7", su.userId());
        assertEquals("KM", su.nickname());
        assertEquals(null, su.headUrl());
        assertEquals(null, su.nationality());
        assertEquals(3, su.role());
        assertEquals(0, su.seatId());
        assertEquals(-1, su.bubbleId());
        assertEquals("#ffffff", su.bubbleColor());
        assertFalse(su.isOnStage());
        assertFalse(su.fgIsActive());
    }

    @Test
    void commentCarriesMsgIdServerTimeAndSourceFromLiveWSSMessage() {
        // The Android LiveWSSMessage sub-object carries msg_id/server_ts/send_time/msg_model/
        // source/from_profile_ts that the previous mapper dropped — verify they're surfaced.
        RoomRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":"25","notify_info":{
              "_id":"c1","created_at":1750622060,"user_id":"131331894","nickname":"J","head_url":"https://h",
              "role":3,"vip_type":2,"day_rank_level":5,"gift_level":7,
              "fg_level":3,"fg_name":"Buddies","fg_is_active":true,
              "bubble_id":4,"bubble_url":"https://bubble","bubble_color":"#abcdef",
              "hit_bad":0,"bubble_animal_type":1,"bubble_animal_url":"https://animal",
              "msg":{"text":{"text":"hi"},"msg_id":"srv-msg-1",
                "server_ts":1750622065,"send_time":1750622064,
                "msg_model":"v2","source":"app","from_profile_ts":1,
                "reply_info":{"msg_id":"c0","from_id":155,"from_nickname":"MD","text":"orig","msg_type":"text","send_time":1750622050}}
            }},"msg_id":"m"}
            """).orElseThrow();

        var c = assertInstanceOf(RoomRealtimeEvent.Comment.class, event).comment();
        assertEquals("srv-msg-1", c.msgId());
        assertEquals(1750622065000L, c.serverTime());
        assertEquals(1750622064000L, c.sendTime());
        assertEquals("v2", c.msgModel());
        assertEquals("app", c.source());
        assertEquals(1, c.fromProfileTs());
        assertEquals(1750622050000L, c.replyInfo().sendTime());
    }

    @Test
    void giftWishEntityMapsFromItsWireShape() throws Exception {
        // LiveWSSGiftWish — goal-gift running total. No live capture yet for the notify_type it
        // rides on, but the entity mapper is exercised here so the JSON parsing stays correct.
        RoomRealtimeEvent.GiftWish wish = mapper.mapGiftWish(om.readTree("""
            {"gift_id":1120,"small_pic":"https://x/gift.png",
             "config_gift_count":100,"received_gift_count":42,"virtual_val":4200}
            """));
        assertEquals(1120L, wish.giftId());
        assertEquals("https://x/gift.png", wish.smallPic());
        assertEquals(100, wish.configGiftCount());
        assertEquals(42, wish.receivedGiftCount());
        assertEquals(4200L, wish.virtualVal());
    }

    @Test
    void rewardAndRewardInfoMapWithNestedRewardList() throws Exception {
        // LiveWSSReward + LiveWSSRewardInfo — the per-user reward envelope.
        RoomRealtimeEvent.RewardInfo ri = mapper.mapRewardInfo(om.readTree("""
            {"user_id":"9","nickname":"K","head_url":"https://h","nation":"JP",
             "reward_list":[
              {"reward_id":1,"award_type":2,"name":"Golden Crown","number":3,
               "animal_type":1,"animal_url":"https://a/1.png","virtual_val":300,"is_mystery_gift":false},
              {"reward_id":2,"award_type":3,"name":"Mystery Box","number":1,
               "animal_type":0,"animal_url":null,"virtual_val":1000,"is_mystery_gift":true}
             ]}
            """));
        assertEquals("9", ri.userId());
        assertEquals("K", ri.nickname());
        assertEquals("JP", ri.nation());
        assertEquals(2, ri.rewards().size());

        var first = ri.rewards().get(0);
        assertEquals(1L, first.rewardId());
        assertEquals(2, first.awardType());
        assertEquals("Golden Crown", first.name());
        assertEquals(3, first.number());
        assertEquals(1, first.animalType());
        assertEquals("https://a/1.png", first.animalUrl());
        assertEquals(300L, first.virtualVal());
        assertFalse(first.isMysteryGift());

        var second = ri.rewards().get(1);
        assertEquals("Mystery Box", second.name());
        assertTrue(second.isMysteryGift());
    }

    @Test
    void purchaseVipMapsAllNineFields() throws Exception {
        RoomRealtimeEvent.PurchaseVip v = mapper.mapPurchaseVip(om.readTree("""
            {"cname":"VR_1","send_uid":"99","gift_id":50,"gift_name":"VIP Month",
             "gift_type":7,"gift_number":1,"label":"VIP","small_pic":"https://s/vip.png","title":"Bought VIP!"}
            """));
        assertEquals("VR_1", v.cname());
        assertEquals("99", v.sendUid());
        assertEquals(50L, v.giftId());
        assertEquals("VIP Month", v.giftName());
        assertEquals(7, v.giftType());
        assertEquals(1, v.giftNumber());
        assertEquals("VIP", v.label());
        assertEquals("https://s/vip.png", v.smallPic());
        assertEquals("Bought VIP!", v.title());
    }

    @Test
    void receiveVipGiftsMapsFromItsWireShape() throws Exception {
        RoomRealtimeEvent.ReceiveVipGifts v = mapper.mapReceiveVipGifts(om.readTree("""
            {"cname":"VR_1","send_user_id":"99","send_nick_name":"Santa",
             "send_type":3,"vip_time":1750622060,"show_time":1750622070}
            """));
        assertEquals("VR_1", v.cname());
        assertEquals("99", v.sendUserId());
        assertEquals("Santa", v.sendNickName());
        assertEquals(3, v.sendType());
        assertEquals(1750622060000L, v.vipTime());
        assertEquals(1750622070000L, v.showTime());
    }

    @Test
    void treasureRewardComposesCampResultAndRewardInfoWithAllStyleFields() throws Exception {
        // LiveWSSTreasureReward — the big colored popup with rankings + camp vote tally.
        RoomRealtimeEvent.TreasureReward t = mapper.mapTreasureReward(om.readTree("""
            {"title":"Battle Royale","task_type_new":"tr","open_cycle":1,"open_level":5,
             "animal_type":2,"animal_url":"https://a/t.png",
             "camp_result":{"option_left_name":"Yes","option_right_name":"No","option_result":1,
                            "vote_count_left":42,"vote_count_right":7},
             "reward_info":{"user_id":"1","nickname":"Winner","head_url":"https://h","nation":"JP",
                            "reward_list":[{"reward_id":1,"award_type":1,"name":"Crown","number":1,
                                            "animal_type":0,"animal_url":null,"virtual_val":1,"is_mystery_gift":false}]},
             "participate_user_ids":["1","2","3"],"reward_user_ids":["1"],"no_privilege_user_ids":["4"],
             "reward_popup_color":"#ff0000","main_text_color":"#ffffff","sub_text_color":"#cccccc","task_desc_color":"#000000"}
            """));
        assertEquals("Battle Royale", t.title());
        assertEquals("tr", t.taskTypeNew());
        assertEquals(1, t.openCycle());
        assertEquals(5, t.openLevel());
        assertEquals(2, t.animalType());
        assertEquals("https://a/t.png", t.animalUrl());

        var cr = t.campResult();
        assertEquals("Yes", cr.optionLeftName());
        assertEquals("No", cr.optionRightName());
        assertEquals(1, cr.optionResult());
        assertEquals(42, cr.voteCountLeft());
        assertEquals(7, cr.voteCountRight());

        assertEquals("Winner", t.rewardInfo().nickname());
        assertEquals(1, t.rewardInfo().rewards().size());
        assertEquals("Crown", t.rewardInfo().rewards().get(0).name());

        assertEquals(3, t.participateUserIds().size());
        assertEquals(1, t.rewardUserIds().size());
        assertEquals(1, t.noPrivilegeUserIds().size());
        assertEquals("#ff0000", t.rewardPopupColor());
        assertEquals("#ffffff", t.mainTextColor());
        assertEquals("#cccccc", t.subTextColor());
        assertEquals("#000000", t.taskDescColor());
    }

    @Test
    void notifyType12RoutesToGiftWishByShape() throws Exception {
        // Android BaseLiveFragment.onRoomUserMessageEvent switches on type 12 but the upstream
        // hasn't been observed sending GiftWish on that exact type; shape-based routing picks it
        // up if it ever arrives regardless of notify_type.
        RoomRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":"12","notify_info":{
              "gift_id":1120,"small_pic":"https://x/gift.png",
              "config_gift_count":100,"received_gift_count":42,"virtual_val":4200,
              "cname":"VR_1"
            }},"msg_id":"m"}
            """).orElseThrow();
        var wish = assertInstanceOf(RoomRealtimeEvent.GiftWish.class, event);
        assertEquals(1120L, wish.giftId());
        assertEquals(42, wish.receivedGiftCount());
    }

    @Test
    void notifyType50RoutesToTreasureRewardByShape() throws Exception {
        RoomRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":"50","notify_info":{
              "title":"Battle","cname":"VR_1",
              "camp_result":{"option_left_name":"A","option_right_name":"B","option_result":1,
                             "vote_count_left":10,"vote_count_right":5},
              "reward_info":{"user_id":"1","nickname":"W","head_url":"https://h","nation":"JP",
                             "reward_list":[]},
              "reward_popup_color":"#ff0000","main_text_color":"#fff"
            }},"msg_id":"m"}
            """).orElseThrow();
        var t = assertInstanceOf(RoomRealtimeEvent.TreasureReward.class, event);
        assertEquals("Battle", t.title());
        assertEquals(10, t.campResult().voteCountLeft());
    }

    @Test
    void notifyType58RoutesToPurchaseVipByShape() throws Exception {
        RoomRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":"58","notify_info":{
              "cname":"VR_1","send_uid":"99","gift_id":50,"gift_name":"VIP Month",
              "gift_type":7,"gift_number":1,"label":"VIP",
              "small_pic":"https://s/vip.png","title":"Bought VIP!"
            }},"msg_id":"m"}
            """).orElseThrow();
        var v = assertInstanceOf(RoomRealtimeEvent.PurchaseVip.class, event);
        assertEquals("VIP Month", v.giftName());
        assertEquals("Bought VIP!", v.title());
    }

    @Test
    void notifyType59RoutesToReceiveVipGiftsByShape() throws Exception {
        RoomRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":"59","notify_info":{
              "cname":"VR_1","send_user_id":"99","send_nick_name":"Santa",
              "send_type":3,"vip_time":1750622060,"show_time":1750622070
            }},"msg_id":"m"}
            """).orElseThrow();
        var v = assertInstanceOf(RoomRealtimeEvent.ReceiveVipGifts.class, event);
        assertEquals("Santa", v.sendNickName());
        assertEquals(3, v.sendType());
    }

    @Test
    void fgUpgradeAwardEntityMapperCalledDirectly() throws Exception {
        // Direct call to the package-private entity mapper (used when a real frame lands and we
        // want to bypass the shape-routing heuristic). Mirrors the other entity-mapper tests.
        RoomRealtimeEvent.FgUpgradeAward f = mapper.mapFgUpgradeAward(om.readTree("""
            {"id":42,"typ":"fg","icon":"https://i/crown.png","content":"Reached Level 5!"}
            """));
        assertEquals(42L, f.id());
        assertEquals("fg", f.awardType());
        assertEquals("https://i/crown.png", f.icon());
        assertEquals("Reached Level 5!", f.content());
    }

    @Test
    void fgUpgradeAwardRoutesByShape() throws Exception {
        RoomRealtimeEvent event = mapper.map("""
            {"event":{"notify_type":"12","notify_info":{
              "id":42,"typ":"fg","icon":"https://i/crown.png","content":"Reached Level 5!"
            }},"msg_id":"m"}
            """).orElseThrow();
        var f = assertInstanceOf(RoomRealtimeEvent.FgUpgradeAward.class, event);
        assertEquals(42L, f.id());
        assertEquals("Reached Level 5!", f.content());
    }

    @Test
    void notifyType12WithUnknownShapeFallsThroughToRaw() {
        // Shape doesn't match any known entity — preserve the frame as Raw.
        RoomRealtimeEvent event = mapper.map(
            "{\"event\":{\"notify_type\":\"12\",\"notify_info\":{\"foo\":\"bar\"}},\"msg_id\":\"m\"}").orElseThrow();
        assertEquals("12", assertInstanceOf(RoomRealtimeEvent.Raw.class, event).originalType());
    }
}
