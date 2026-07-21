package com.jilali.roomcontext.infrastructure.dto.stage;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Serdeable
public record StageInviteRequest(
        @JsonProperty("invite_type") int inviteType,
        @JsonProperty("user_id") @Positive long userId,
        @NotBlank String cname,
        @JsonProperty("busi_type") int busiType) {
}
