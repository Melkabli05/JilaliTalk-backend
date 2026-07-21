package com.jilali.roomcontext.infrastructure.dto.signin;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Serdeable
public record ClaimRewardRequest(@JsonProperty("host_id") @Positive long hostId, @NotBlank String cname) {}
