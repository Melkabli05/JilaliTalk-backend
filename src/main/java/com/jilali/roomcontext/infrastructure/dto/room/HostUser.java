package com.jilali.roomcontext.infrastructure.dto.room;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record HostUser(
        @JsonProperty("user_id") long userId,
        @Nullable String nickname,
        @JsonProperty("head_url") @Nullable String headUrl,
        @Nullable String nationality) {
}
