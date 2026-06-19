package com.jilali.room.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliEnvelope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VoiceRoomInfoResponseTest {
    private final ObjectMapper om = new ObjectMapper();

    /**
     * LiveHub returns {@code luck_bag} as a JSON object whenever a room has an active lucky-bag
     * giveaway (only {@code null} when none is running), which is what most captured traffic
     * shows. A {@code String}-typed field can't bind that object, and Jackson throws.
     */
    @Test
    void decodesActiveLuckyBagObjectWithoutThrowing() throws Exception {
        String json = """
            {"code":0,"msg":"ok","data":{
              "host_info":null,
              "req_user_info":null,
              "channel_info":null,
              "config_info":null,
              "luck_bag":{"lucky_bag_id":"lb_1","lucky_bag_number":3,"room_id":"VR_1"},
              "room_level_info":null,
              "managers":null
            }}
            """;

        JilaliEnvelope<VoiceRoomInfoResponse> envelope =
                om.readValue(json, new TypeReference<JilaliEnvelope<VoiceRoomInfoResponse>>() {});

        assertNotNull(envelope.data().luckBag());
        assertEquals("lb_1", envelope.data().luckBag().luckyBagId());
    }

    /**
     * LiveHub returns {@code pinned_comment.comment_closed_friend} as a JSON object describing
     * the commenter's closed-friend/couple level whenever that feature is active for the pinned
     * comment (only {@code null} otherwise). A {@code String}-typed field can't bind that object,
     * and Jackson throws.
     */
    @Test
    void decodesPinnedCommentObjectFieldsWithoutThrowing() throws Exception {
        String json = """
            {"code":0,"msg":"ok","data":{
              "host_info":null,
              "req_user_info":null,
              "channel_info":{
                "pinned_comment":{
                  "_id":"c1",
                  "cname":"VR_1",
                  "user_id":1,
                  "msg":{"text":{"text":"hello room"}},
                  "comment_closed_friend":{
                    "couple_max_level":0,
                    "couple_max_level_icon":"",
                    "best_max_level":2,
                    "best_max_level_icon":"https://example.com/icon.png"
                  },
                  "user_extra_info":{
                    "hide_vip_identity":0,
                    "is_expert":true,
                    "is_new_user":false,
                    "vip_plus_expire_ts":0,
                    "vip_plus_logo":"",
                    "vip_plus_logo_anim":""
                  }
                }
              },
              "config_info":null,
              "room_level_info":null,
              "managers":null
            }}
            """;

        JilaliEnvelope<VoiceRoomInfoResponse> envelope =
                om.readValue(json, new TypeReference<JilaliEnvelope<VoiceRoomInfoResponse>>() {});

        var pinnedComment = envelope.data().channelInfo().pinnedComment();

        var closedFriend = pinnedComment.commentClosedFriend();
        assertNotNull(closedFriend);
        assertEquals(2, closedFriend.bestMaxLevel());

        assertNotNull(pinnedComment.msg());
        assertNotNull(pinnedComment.msg().text());
        assertEquals("hello room", pinnedComment.msg().text().text());

        var userExtraInfo = pinnedComment.userExtraInfo();
        assertNotNull(userExtraInfo);
        assertEquals(true, userExtraInfo.isExpert());
    }
}
