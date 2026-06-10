package com.jilali.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record HostUser(
        @JsonProperty("user_id") long userId,
        String nickname,
        @JsonProperty("head_url") @Nullable String headUrl,
        @Nullable String nationality) {
}
