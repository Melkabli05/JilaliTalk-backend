package com.jilali.vip.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Whether a given VIP experience-card feature (e.g. the 24h VIP perk, {@code scene_id=30000} /
 * {@code feature_id=00001}) is currently active for the user, and when it expires.
 */
@Serdeable
public record VipFeatureRightResponse(
    @JsonProperty("effect_status") int effectStatus,
    @JsonProperty("left_times") int leftTimes,
    @JsonProperty("expire_at") long expireAt,
    @JsonProperty("time_now") long timeNow
) {}
