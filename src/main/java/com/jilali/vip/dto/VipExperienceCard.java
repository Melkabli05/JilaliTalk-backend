package com.jilali.vip.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * A VIP experience card owned by a user. {@code usedFeatures} is only present once at least one
 * of the card's {@code card_features} has been activated via {@code user_use_card}.
 */
@Serdeable
public record VipExperienceCard(
    long id,
    @JsonProperty("get_at") long getAt,
    VipExperienceCardDetail detail,
    @JsonProperty("used_features") @Nullable List<VipExperienceCardUsedFeature> usedFeatures,
    String source,
    @JsonProperty("record_id") String recordId
) {}
