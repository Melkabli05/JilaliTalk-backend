package com.jilali.roomcontext.infrastructure.dto.vip;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Serdeable
public record UseVipExperienceCardRequest(
    @JsonProperty("card_id") @Positive long cardId,
    @JsonProperty("feature_id") @NotBlank String featureId,
    @JsonProperty("scene_id") @NotBlank String sceneId,
    @JsonProperty("user_id") @Positive long userId,
    @NotBlank String version
) {}
