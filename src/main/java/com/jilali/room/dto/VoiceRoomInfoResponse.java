package com.jilali.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record VoiceRoomInfoResponse(
        @JsonProperty("host_info") @Nullable HostInfo hostInfo,
        @JsonProperty("req_user_info") @Nullable ReqUserInfo reqUserInfo,
        @JsonProperty("channel_info") @Nullable ChannelInfo channelInfo) {

    @Serdeable
    public record HostInfo(
            @JsonProperty("user_id") long userId,
            @Nullable UserBase base,
            @JsonProperty("is_teacher") boolean isTeacher,
            @JsonProperty("is_expert") boolean isExpert) {
    }

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
            @JsonProperty("payment_status_for_session") boolean paymentStatusForSession) {
    }

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
            @JsonProperty("rtc_info") @Nullable RtcInfo rtcInfo) {

        @Serdeable
        public record RtcInfo(
                @JsonProperty("app_id") @Nullable String appId,
                @Nullable String token,
                @JsonProperty("engine") @Nullable String engine) {
        }
    }
}