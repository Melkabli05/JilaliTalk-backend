package com.jilali.roomcontext.infrastructure.dto.room;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record BatchChannelStatus(
        String cname,
        @JsonProperty("room_status") int roomStatus,
        @JsonProperty("ended_at") @Nullable Long endedAt) {
}
