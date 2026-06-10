package com.jilali.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record Channel(
        String cname,
        @JsonProperty("busi_type") int busiType,
        String name,
        @Nullable String description,
        @JsonProperty("lang_id") int langId,
        @Nullable List<Integer> langs,
        @JsonProperty("room_status") int roomStatus,
        @JsonProperty("total_user_count") int totalUserCount,
        @JsonProperty("heat_value") @Nullable Integer heatValue) {
}
