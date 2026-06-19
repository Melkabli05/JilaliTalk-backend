package com.jilali.vip.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Serdeable
public record VipFeatureRightRequest(
    @JsonProperty("user_id") @Positive long userId,
    @JsonProperty("feature_id") @NotBlank String featureId,
    @JsonProperty("scene_id") @NotBlank String sceneId
) {}
