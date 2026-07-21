package com.jilali.roomcontext.infrastructure.dto.vip;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record VipExperienceCardFeature(
    @JsonProperty("scene_id") String sceneId,
    @JsonProperty("feature_id") String featureId,
    String ext,
    @JsonProperty("card_type") String cardType
) {}
