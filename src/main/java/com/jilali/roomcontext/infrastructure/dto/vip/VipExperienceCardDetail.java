package com.jilali.roomcontext.infrastructure.dto.vip;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record VipExperienceCardDetail(
    int id,
    @JsonProperty("card_id") long cardId,
    @JsonProperty("c_id") int cId,
    long duration,
    String version,
    @JsonProperty("card_type") int cardType,
    @JsonProperty("card_features") @Nullable List<VipExperienceCardFeature> cardFeatures,
    int status,
    @JsonProperty("time_type") int timeType,
    @JsonProperty("receive_use_expire_duration") long receiveUseExpireDuration
) {}
