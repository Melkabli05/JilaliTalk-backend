package com.jilali.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record VoiceRoomInfoResponse(
        @JsonProperty("host_info") @Nullable HostInfo hostInfo,
        @JsonProperty("req_user_info") @Nullable ReqUserInfo reqUserInfo,
        @JsonProperty("channel_info") @Nullable ChannelInfo channelInfo) {

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
                new ChannelInfo.RtcInfo(rtc.appId(), token, rtc.engine()));
        return new VoiceRoomInfoResponse(hostInfo, reqUserInfo, channel);
    }

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