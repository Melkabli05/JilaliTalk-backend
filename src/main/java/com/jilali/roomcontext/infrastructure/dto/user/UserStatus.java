package com.jilali.roomcontext.infrastructure.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record UserStatus(
        @JsonProperty("user_status_type") int userStatusType,
        @JsonProperty("user_id") long userId,
        @JsonProperty("room_id") @Nullable String roomId,
        @JsonProperty("room_name") @Nullable String roomName,
        @JsonProperty("host_id") @Nullable Long hostId,
        @JsonProperty("host_name") @Nullable String hostName,
        @JsonProperty("host_nationality") @Nullable String hostNationality,
        @Nullable String cname,
        @JsonProperty("head_url") @Nullable String headUrl,
        @JsonProperty("gift_level") @Nullable Integer giftLevel,
        boolean blackened) {
}
