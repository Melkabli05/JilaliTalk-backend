package com.jilali.stage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Serdeable
public record RaiseHandApprovalRequest(
        @JsonProperty("busi_type") int busiType,
        @JsonProperty("user_id") @Positive long userId,
        @JsonProperty("approval_type") int approvalType,
        @NotBlank String cname) {
}
