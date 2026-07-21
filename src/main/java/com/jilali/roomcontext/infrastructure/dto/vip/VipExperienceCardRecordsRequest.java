package com.jilali.roomcontext.infrastructure.dto.vip;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Positive;

@Serdeable
public record VipExperienceCardRecordsRequest(
    @JsonProperty("user_id") @Positive long userId,
    @JsonProperty("with_valid_filter") boolean withValidFilter,
    @JsonProperty("with_detail") boolean withDetail
) {}
