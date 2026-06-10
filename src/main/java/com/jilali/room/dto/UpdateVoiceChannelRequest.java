package com.jilali.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@Serdeable
public record UpdateVoiceChannelRequest(
        @NotBlank String cname,
        @JsonProperty("manager_uids") @Nullable List<Long> managerUids,
        @Nullable List<Integer> types) {
}
