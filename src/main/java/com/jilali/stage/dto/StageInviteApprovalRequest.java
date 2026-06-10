package com.jilali.stage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record StageInviteApprovalRequest(
        @NotBlank String cname,
        @JsonProperty("busi_type") int busiType,
        @JsonProperty("invite_type") int inviteType,
        @JsonProperty("approval_type") int approvalType) {
}
