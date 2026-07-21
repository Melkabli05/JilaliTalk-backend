package com.jilali.roomcontext.infrastructure.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record HostStatus(
        @JsonProperty("is_live_host") boolean isLiveHost,
        @JsonProperty("is_voice_room_host") boolean isVoiceRoomHost,
        @JsonProperty("is_banned_live") boolean isBannedLive,
        @JsonProperty("is_banned_voice_room") boolean isBannedVoiceRoom,
        @JsonProperty("is_black_hole_user") boolean isBlackHoleUser,
        @JsonProperty("is_hide_user") boolean isHideUser,
        @JsonProperty("is_minor") boolean isMinor,
        @JsonProperty("is_apply_live") boolean isApplyLive,
        @JsonProperty("is_apply_voice") boolean isApplyVoice,
        @JsonProperty("is_show_total_ranking_list") boolean isShowTotalRankingList) {
}
