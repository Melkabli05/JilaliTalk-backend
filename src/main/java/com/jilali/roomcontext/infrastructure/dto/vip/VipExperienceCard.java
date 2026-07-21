package com.jilali.roomcontext.infrastructure.dto.vip;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record VipExperienceCard(
    long id,
    @JsonProperty("get_at") long getAt,
    VipExperienceCardDetail detail,
    @JsonProperty("used_features") @Nullable List<VipExperienceCardUsedFeature> usedFeatures,
    String source,
    @JsonProperty("record_id") String recordId
) {}
