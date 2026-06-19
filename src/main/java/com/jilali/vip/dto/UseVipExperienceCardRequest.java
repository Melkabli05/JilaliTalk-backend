package com.jilali.vip.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Activates one perk of an owned VIP experience card — this is the "claim" action. For the 24h
 * VIP perk, callers pass {@code sceneId=30000}, {@code featureId=00001}.
 */
@Serdeable
public record UseVipExperienceCardRequest(
    @JsonProperty("card_id") @Positive long cardId,
    @JsonProperty("feature_id") @NotBlank String featureId,
    @JsonProperty("scene_id") @NotBlank String sceneId,
    @JsonProperty("user_id") @Positive long userId,
    @NotBlank String version
) {}
