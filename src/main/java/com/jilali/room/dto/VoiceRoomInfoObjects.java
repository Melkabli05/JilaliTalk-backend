package com.jilali.room.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

/** Additional nested DTOs for fields in the LiveHub voice_room_info response. */
public final class VoiceRoomInfoObjects {

    private VoiceRoomInfoObjects() {}

    // ---- channel_info sub-objects ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Serdeable
    public record PinnedComment(
            @JsonProperty("_id") String id,
            @JsonProperty("created_at") long createdAt,
            @JsonProperty("updated_at") long updatedAt,
            @JsonProperty("cname") String cname,
            @JsonProperty("busi_type") int busiType,
            @JsonProperty("user_id") long userId,
            @Nullable String nickname,
            @JsonProperty("head_url") @Nullable String headUrl,
            @Nullable String nationality,
            int role,
            @JsonProperty("vip_type") int vipType,
            @JsonProperty("msg") @Nullable Map<String, Object> msg,
            @JsonProperty("day_rank_level") int dayRankLevel,
            @JsonProperty("gift_level") int giftLevel,
            @JsonProperty("fg_level") int fgLevel,
            @JsonProperty("fg_name") @Nullable String fgName,
            @JsonProperty("fg_is_active") boolean fgIsActive,
            @JsonProperty("bubble_id") int bubbleId,
            @JsonProperty("bubble_url") @Nullable String bubbleUrl,
            @JsonProperty("bubble_color") @Nullable String bubbleColor,
            @JsonProperty("hit_bad") int hitBad,
            @JsonProperty("bubble_animal_type") int bubbleAnimalType,
            @JsonProperty("bubble_animal_url") @Nullable String bubbleAnimalUrl,
            @JsonProperty("vip_logo") @Nullable String vipLogo,
            @JsonProperty("vip_logo_anim") @Nullable String vipLogoAnim,
            @Nullable String expireAt,
            @JsonProperty("medal_wall_icon") @Nullable String medalWallIcon,
            @JsonProperty("comment_closed_friend") @Nullable String commentClosedFriend,
            @JsonProperty("user_extra_info") @Nullable Map<String, Object> userExtraInfo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Serdeable
    public record SayGuessInfo(
            @JsonProperty("session_id") @Nullable String sessionId,
            @JsonProperty("question_set_id") @Nullable String questionSetId,
            @Nullable String theme,
            @Nullable String aiTheme,
            @JsonProperty("question_count") int questionCount,
            @JsonProperty("answer_duration_sec") int answerDurationSec,
            @JsonProperty("started_at") long startedAt,
            @JsonProperty("ended_at") long endedAt,
            @JsonProperty("answered_count") int answeredCount,
            @JsonProperty("total_count") int totalCount,
            @JsonProperty("today_used_count") int todayUsedCount,
            @JsonProperty("room_level_play_freely") int roomLevelPlayFreely,
            @JsonProperty("pay_start_game_coins") int payStartGameCoins,
            @Nullable String gameUrl,
            @JsonProperty("game_status") int gameStatus,
            @JsonProperty("say_guess_game_end") @Nullable String sayGuessGameEnd,
            @JsonProperty("user_answers") List<Object> userAnswers,
            @Nullable List<Question> questions) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        @Serdeable
        public record Question(
                @JsonProperty("question_index") int questionIndex,
                @Nullable String content,
                @Nullable List<Option> options,
                @JsonProperty("correct_index") int correctIndex) {

            @Serdeable
            public record Option(
                    int index,
                    @Nullable String content) {}
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Serdeable
    public record WhiteboardInfo(
            @JsonProperty("whiteboard_enabled") boolean whiteboardEnabled,
            @JsonProperty("whiteboard_engine") int whiteboardEngine,
            @JsonProperty("whiteboard_app_id") @Nullable String whiteboardAppId,
            @JsonProperty("whiteboard_uuid") @Nullable String whiteboardUuid,
            @Nullable String token) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Serdeable
    public record WhiteboardSettings(
            @JsonProperty("dnd_hide_welcome") boolean dndHideWelcome,
            @JsonProperty("dnd_hide_gift_effect") boolean dndHideGiftEffect,
            @JsonProperty("dnd_limit_comment") boolean dndLimitComment,
            @JsonProperty("speak_mode") int speakMode) {}

    // ---- top-level room fields ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Serdeable
    public record RoomBackground(
            @JsonProperty("background_id") int backgroundId,
            @JsonProperty("list_background_url") @Nullable String listBackgroundUrl,
            @JsonProperty("top_list_background_url") @Nullable String topListBackgroundUrl,
            @JsonProperty("min_background_url") @Nullable String minBackgroundUrl,
            @JsonProperty("room_big_background_url") @Nullable String roomBigBackgroundUrl,
            @JsonProperty("background_paid") int backgroundPaid,
            @JsonProperty("share_background_pic") @Nullable String shareBackgroundPic,
            @JsonProperty("share_background_pic_dark") @Nullable String shareBackgroundPicDark,
            @JsonProperty("list_background_url_dark") @Nullable String listBackgroundUrlDark,
            @JsonProperty("background_animal_type") int backgroundAnimalType,
            @JsonProperty("background_animal_url") @Nullable String backgroundAnimalUrl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Serdeable
    public record CaptionInfo(
            @JsonProperty("room_caption_status") int roomCaptionStatus,
            @JsonProperty("user_caption_status") int userCaptionStatus,
            @JsonProperty("caption_lang") int captionLang,
            @JsonProperty("caption_engine_type") int captionEngineType,
            @JsonProperty("stt_key") @Nullable String sttKey,
            @JsonProperty("can_try_voice_caption") boolean canTryVoiceCaption,
            @JsonProperty("is_room_caption_non_try_out_enough") boolean isRoomCaptionNonTryOutEnough,
            @JsonProperty("advertise_reward_expired_ts") long advertiseRewardExpiredTs,
            @JsonProperty("translate_langs") @Nullable List<TranslateLang> translateLangs,
            @JsonProperty("current_translate_lang") @Nullable TranslateLang currentTranslateLang,
            @JsonProperty("max_show_cnt") int maxShowCnt) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        @Serdeable
        public record TranslateLang(
                @JsonProperty("lang_id") int langId,
                @JsonProperty("stt_key") @Nullable String sttKey) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Serdeable
    public record UserPaidExposureData(
            @JsonProperty("current_exposures") int currentExposures,
            @JsonProperty("can_purchase_more") boolean canPurchaseMore,
            @JsonProperty("remain_exposures") int remainExposures) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Serdeable
    public record QuickChatInfo(
            @JsonProperty("host_collapsed") boolean hostCollapsed,
            @JsonProperty("quick_chat_show_min") int quickChatShowMin,
            @JsonProperty("no_show_days") int noShowDays,
            @JsonProperty("no_click_send_times") int noClickSendTimes) {}
}
