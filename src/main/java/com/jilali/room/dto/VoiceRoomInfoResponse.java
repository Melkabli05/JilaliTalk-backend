package com.jilali.room.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jilali.room.dto.VoiceRoomInfoObjects.CaptionInfo;
import com.jilali.room.dto.VoiceRoomInfoObjects.LuckBag;
import com.jilali.room.dto.VoiceRoomInfoObjects.PinnedComment;
import com.jilali.room.dto.VoiceRoomInfoObjects.QuickChatInfo;
import com.jilali.room.dto.VoiceRoomInfoObjects.RoomBackground;
import com.jilali.room.dto.VoiceRoomInfoObjects.SayGuessInfo;
import com.jilali.room.dto.VoiceRoomInfoObjects.UserPaidExposureData;
import com.jilali.room.dto.VoiceRoomInfoObjects.WhiteboardInfo;
import com.jilali.room.dto.VoiceRoomInfoObjects.WhiteboardSettings;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Serdeable
public record VoiceRoomInfoResponse(
        @JsonProperty("host_info") @Nullable HostInfo hostInfo,
        @JsonProperty("req_user_info") @Nullable ReqUserInfo reqUserInfo,
        @JsonProperty("channel_info") @Nullable ChannelInfo channelInfo,
        @JsonProperty("config_info") @Nullable ConfigInfo configInfo,
        @JsonProperty("room_level_info") @Nullable RoomLevelInfo roomLevelInfo,
        @Nullable List<ManagerInfo> managers,
        @JsonProperty("luck_bag") @Nullable LuckBag luckBag,
        @JsonProperty("room_background") @Nullable RoomBackground roomBackground,
        @JsonProperty("caption_info") @Nullable CaptionInfo captionInfo,
        @JsonProperty("rtc_info") @Nullable RtcInfoOuter rtcInfoOuter,
        @JsonProperty("user_paid_exposure_data") @Nullable UserPaidExposureData userPaidExposureData,
        @JsonProperty("quick_chat_info") @Nullable QuickChatInfo quickChatInfo) {

    /**
     * Returns a copy with the RTC token replaced, or {@code this} when there is no {@code rtc_info}.
     * Swaps the AES-encrypted upstream token for the plain Agora token before responding, keeping
     * the immutable-record rebuild next to the data instead of in the controller.
     */
    public VoiceRoomInfoResponse withRtcToken(String token) {
        if (channelInfo == null || channelInfo.rtcInfo() == null) {
            return this;
        }
        var rtc = channelInfo.rtcInfo();
        var channel = new ChannelInfo(
                channelInfo.name(), channelInfo.langId(), channelInfo.langs(), channelInfo.topic(),
                channelInfo.notice(), channelInfo.noticePinType(), channelInfo.hourRank(),
                channelInfo.topLastHourRanking(),
                channelInfo.pinnedComment(), channelInfo.allowCommentStatus(), channelInfo.roomStatus(),
                channelInfo.startedAt(), channelInfo.audienceCount(), channelInfo.raiseHandUserCount(),
                channelInfo.onMicCount(), channelInfo.totalActiveNum(), channelInfo.roomDuration(),
                channelInfo.visibleStatus(), channelInfo.category(), channelInfo.privateRoomKey(),
                channelInfo.categoryTopicTag(), channelInfo.gameType(), channelInfo.tagType(),
                channelInfo.hasTreasureBox(),
                channelInfo.sayGuessInfo(),
                channelInfo.whiteboardInfo(),
                channelInfo.whiteboardSettings(),
                new ChannelInfo.RtcInfo(rtc.appId(), token, rtc.engine()));
        return new VoiceRoomInfoResponse(
                hostInfo, reqUserInfo, channel, configInfo, roomLevelInfo, managers,
                luckBag, roomBackground, captionInfo, rtcInfoOuter, userPaidExposureData, quickChatInfo);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Serdeable
    public record RtcInfoOuter(
            @JsonProperty("app_id") @Nullable String appId,
            @Nullable String token,
            @JsonProperty("engine") @Nullable String engine) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Serdeable
    public record HostInfo(
            @JsonProperty("user_id") long userId,
            @Nullable UserBase base,
            @JsonProperty("is_teacher") boolean isTeacher,
            @JsonProperty("is_expert") boolean isExpert) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Serdeable
    public record ReqUserInfo(
            @JsonProperty("user_id") long userId,
            @Nullable UserBase base,
            int role,
            @JsonProperty("is_mute") boolean isMute,
            @JsonProperty("is_banned_comment") boolean isBannedComment,
            @JsonProperty("relation_type") int relationType,
            @JsonProperty("is_on_mic") boolean isOnMic,
            @JsonProperty("is_turn_on_mic") boolean isTurnOnMic,
            @JsonProperty("payment_status_for_session") boolean paymentStatusForSession,
            @Nullable Ripple ripple,
            @JsonProperty("internal_user") boolean internalUser,
            @JsonProperty("is_use_happy_chat_card") boolean isUseHappyChatCard,
            @JsonProperty("is_show_week_card") boolean isShowWeekCard,
            @JsonProperty("remain_join_dur_secs") long remainJoinDurSecs,
            @JsonProperty("join_dur_expired_at") long joinDurExpiredAt,
            @JsonProperty("lucky_bag_icon") @Nullable String luckyBagIcon) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        @Serdeable
        public record Ripple(
                @JsonProperty("ripple_id") long rippleId,
                @JsonProperty("ripple_url") @Nullable String rippleUrl,
                @JsonProperty("ripple_animal_type") int rippleAnimalType,
                @JsonProperty("ripple_animal_url") @Nullable String rippleAnimalUrl) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Serdeable
    public record ChannelInfo(
            String name,
            @JsonProperty("lang_id") int langId,
            @JsonProperty("langs") @Nullable int[] langs,
            @Nullable String topic,
            @Nullable String notice,
            @JsonProperty("notice_pin_type") int noticePinType,
            @JsonProperty("hour_rank") int hourRank,
            @JsonProperty("top_last_hour_ranking") boolean topLastHourRanking,
            @JsonProperty("pinned_comment") @Nullable PinnedComment pinnedComment,
            @JsonProperty("allow_comment_status") int allowCommentStatus,
            @JsonProperty("room_status") int roomStatus,
            @JsonProperty("started_at") long startedAt,
            @JsonProperty("audience_count") int audienceCount,
            @JsonProperty("raise_hand_user_count") int raiseHandUserCount,
            @JsonProperty("on_mic_count") int onMicCount,
            @JsonProperty("total_active_num") int totalActiveNum,
            @JsonProperty("room_duration") long roomDuration,
            @JsonProperty("visible_status") int visibleStatus,
            @Nullable String category,
            @JsonProperty("private_room_key") @Nullable String privateRoomKey,
            @JsonProperty("category_topic_tag") @Nullable CategoryTopicTag categoryTopicTag,
            @JsonProperty("game_type") int gameType,
            @JsonProperty("tag_type") int tagType,
            @JsonProperty("has_treasure_box") boolean hasTreasureBox,
            @JsonProperty("say_guess_info") @Nullable SayGuessInfo sayGuessInfo,
            @JsonProperty("whiteboard_info") @Nullable WhiteboardInfo whiteboardInfo,
            @JsonProperty("whiteboard_settings") @Nullable WhiteboardSettings whiteboardSettings,
            @JsonProperty("rtc_info") @Nullable RtcInfo rtcInfo) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        @Serdeable
        public record RtcInfo(
                @JsonProperty("app_id") @Nullable String appId,
                @Nullable String token,
                @JsonProperty("engine") @Nullable String engine) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        @Serdeable
        public record CategoryTopicTag(
                @JsonProperty("category_id") int categoryId,
                @JsonProperty("category_name") @Nullable String categoryName,
                @Nullable String icon,
                @JsonProperty("selected_icon") @Nullable String selectedIcon,
                @JsonProperty("bg_color") @Nullable String bgColor,
                @JsonProperty("font_color") @Nullable String fontColor,
                @JsonProperty("topic_id") int topicId,
                @JsonProperty("topic_name") @Nullable String topicName) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Serdeable
    public record ConfigInfo(
            @JsonProperty("heartbeat_second") int heartbeatSecond,
            @JsonProperty("show_blind_box_gift") boolean showBlindBoxGift,
            @JsonProperty("level_up_show") boolean levelUpShow,
            @JsonProperty("up_level") int upLevel,
            @JsonProperty("gift_send_show") boolean giftSendShow,
            @JsonProperty("entrance_effect_switch_status") boolean entranceEffectSwitchStatus,
            @JsonProperty("gift_effect_switch_status") boolean giftEffectSwitchStatus,
            @JsonProperty("comment_vip_tag_show") boolean commentVipTagShow,
            @JsonProperty("comment_admin_tag_show") boolean commentAdminTagShow,
            @JsonProperty("stage_list_admin_tag_show") boolean stageListAdminTagShow) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Serdeable
    public record RoomLevelInfo(
            int level,
            @JsonProperty("level_icon") @Nullable String levelIcon,
            @JsonProperty("level_rtl_icon") @Nullable String levelRtlIcon,
            @JsonProperty("level_icon_v2") @Nullable String levelIconV2,
            @JsonProperty("level_rtl_icon_v2") @Nullable String levelRtlIconV2,
            @JsonProperty("label_font_color") @Nullable String labelFontColor) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Serdeable
    public record ManagerInfo(
            @JsonProperty("user_id") long userId,
            @JsonProperty("head_url") @Nullable String headUrl,
            @Nullable String nationality,
            @JsonProperty("nick_name") @Nullable String nickName,
            @JsonProperty("user_name") @Nullable String userName,
            @JsonProperty("short_full_py") @Nullable String shortFullPy,
            @JsonProperty("full_py") @Nullable String fullPy,
            @JsonProperty("stay_time") long stayTime,
            @JsonProperty("is_online") int isOnline) {}
}
