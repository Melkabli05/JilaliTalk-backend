package com.jilali.roomcontext.infrastructure.dto.manager;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record ApproveManagerRequest(
        @JsonProperty("operation_type") @NotBlank String operationType,
        @NotBlank String cname,
        @JsonProperty("host_id") long hostId) {
}
