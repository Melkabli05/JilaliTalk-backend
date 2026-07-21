package com.jilali.roomcontext.infrastructure.dto.stage;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record RaiseHandRequest(
        @NotBlank String cname,
        @JsonProperty("raisehand_type") int raisehandType,
        @JsonProperty("busi_type") int busiType) {
}
