package com.jilali.roomcontext.infrastructure.dto.vip;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record VipFeatureRightResponse(
    @JsonProperty("effect_status") int effectStatus,
    @JsonProperty("left_times") int leftTimes,
    @JsonProperty("expire_at") long expireAt,
    @JsonProperty("time_now") long timeNow
) {}
