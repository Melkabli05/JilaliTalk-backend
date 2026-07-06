package com.jilali.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Response from {@code GET /livehub/host_status}. Flags about the CALLING user's own
 * account eligibility to go live/host a voice room — not about a specific room's host
 * being present (see {@link UserStatus} for that). Verified against 39 captures in
 * {@code endpots/organized_captures_new/livehub_host_status.jsonl}.
 */
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
