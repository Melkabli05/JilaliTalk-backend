package com.jilali.roomcontext.infrastructure.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record UserOnlineStatus(
        @JsonProperty("user_id") long userId,
        @JsonProperty("gift_level") @Nullable Integer giftLevel,
        boolean online) {
}
