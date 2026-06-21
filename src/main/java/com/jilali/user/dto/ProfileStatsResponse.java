package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from POST /profile/v1/baseinfo/mnt_info.
 * Uses status/message envelope.
 */
@Serdeable
public record ProfileStatsResponse(
    int status,
    String message,
    @Nullable StatsData data
) {
    @Serdeable
    public record StatsData(
        @JsonProperty("total_mnt_count") int totalMntCount,
        @JsonProperty("total_like_count") int totalLikeCount,
        @JsonProperty("last_mnt_like_count") int lastMntLikeCount,
        @JsonProperty("last_mnt_post_ts") long lastMntPostTs,
        @JsonProperty("registered_ts") long registeredTs
    ) {}
}