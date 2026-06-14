package com.jilali.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record RoomLevelConfigResponse(
    @Nullable List<RoomLevelItem> items
) {

    @Serdeable
    public record RoomLevelItem(
        @JsonProperty("room_level") int roomLevel,
        @JsonProperty("experience") int experience,
        @JsonProperty("level_up_experience") int levelUpExperience,
        @Nullable @JsonProperty("rewards") List<RewardItem> rewards,
        @Nullable @JsonProperty("equitys") List<Equity> equitys
    ) {}

    @Serdeable
    public record RewardItem(
        int id,
        @JsonProperty("gift_id") int giftId,
        int type,
        @JsonProperty("card_type") int cardType,
        String name,
        int number,
        String icon,
        @JsonProperty("multi_name") String multiName
    ) {}

    @Serdeable
    public record Equity(
        int id,
        String name,
        String icon,
        int number,
        @JsonProperty("equity_type") int equityType
    ) {}
}
