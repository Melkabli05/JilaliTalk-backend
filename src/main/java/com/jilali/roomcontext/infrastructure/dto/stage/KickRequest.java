package com.jilali.roomcontext.infrastructure.dto.stage;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Serdeable
public record KickRequest(
        @NotBlank String cname,
        @JsonProperty("user_id") @Positive long userId,
        @JsonProperty("busi_type") int busiType) {
}
