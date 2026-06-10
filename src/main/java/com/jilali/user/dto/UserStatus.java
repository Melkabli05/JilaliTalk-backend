package com.jilali.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record UserStatus(
        @JsonProperty("user_status_type") int userStatusType,
        @JsonProperty("user_id") long userId,
        @JsonProperty("room_name") @Nullable String roomName,
        @Nullable String cname,
        @JsonProperty("gift_level") @Nullable Integer giftLevel,
        boolean blackened) {
}
