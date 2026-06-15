package com.jilali.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/** Kept flat so Jackson deserializes directly from the upstream {@code host_user} shape. */
@Serdeable
public record HostUser(
        @JsonProperty("user_id") long userId,
        @Nullable String nickname,
        @JsonProperty("head_url") @Nullable String headUrl,
        @Nullable String nationality) {
}
