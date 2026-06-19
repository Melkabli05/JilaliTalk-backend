package com.jilali.vip.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

/**
 * One perk a card unlocks, identified by {@code scene_id}/{@code feature_id} (e.g. the 24h VIP
 * perk is {@code scene_id=30000}, {@code feature_id=00001}). {@code ext} is itself a JSON-encoded
 * string upstream (e.g. {@code {"times":0,"duration":86400,"receive_use_expire_duration":2592000}})
 * rather than a nested object, so it is kept raw here instead of guessing a shape for every feature.
 */
@Serdeable
public record VipExperienceCardFeature(
    @JsonProperty("scene_id") String sceneId,
    @JsonProperty("feature_id") String featureId,
    String ext,
    @JsonProperty("card_type") String cardType
) {}
