package com.jilali.roomcontext.infrastructure.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@Serdeable
public record RoomUserListRequest(
        @JsonProperty("get_type") @Nullable List<Integer> getType,
        @NotBlank String cname,
        @JsonProperty("busi_type") int busiType) {
}
