package com.jilali.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record HeartbeatRequest(
        @JsonProperty("host_id") long hostId,
        @JsonProperty("is_fg_member") boolean isFgMember,
        @JsonProperty("busi_type") int busiType,
        @NotBlank String cname) {
}
