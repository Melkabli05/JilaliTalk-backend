package com.jilali.manager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Manager(
        @JsonProperty("user_id") long userId,
        String nickname,
        @JsonProperty("head_url") @Nullable String headUrl,
        @Nullable String nationality,
        int role,
        @JsonProperty("is_in_room") boolean isInRoom,
        @JsonProperty("stay_time") @Nullable Long stayTime) {
}
