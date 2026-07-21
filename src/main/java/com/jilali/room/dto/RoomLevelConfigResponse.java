package com.jilali.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jilali.platform.models.RewardItem;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record RoomLevelConfigResponse(
    @Nullable List<RoomLevelItem> items
) {

    /** A single room level configuration (XP, rewards, equitys). */
    @Serdeable
    public record RoomLevelItem(
        @JsonProperty("room_level") int roomLevel,
        int experience,
        @JsonProperty("big_level") int bigLevel,
        @JsonProperty("big_level_new") int bigLevelNew,
        @JsonProperty("room_style_url") @Nullable String roomStyleUrl,
        @JsonProperty("level_name") @Nullable String levelName,
        @JsonProperty("level_up_experience") int levelUpExperience,
        @Nullable @JsonProperty("rewards") List<RewardItem> rewards,
        @Nullable @JsonProperty("equitys") List<Equity> equitys,
        @Nullable ExpData exp
    ) {}

    @Serdeable
    public record ExpData(
        int exp,
        @JsonProperty("max_exp") int maxExp
    ) {}

    @Serdeable
    public record Equity(
        int id,
        String name,
        @JsonProperty("multi_name") @Nullable String multiName,
        String icon,
        @JsonProperty("multi_content") @Nullable String multiContent,
        int status,
        int sort,
        @JsonProperty("created_at") long createdAt,
        @JsonProperty("updated_at") long updatedAt,
        @JsonProperty("equity_type") int equityType,
        int number,
        @JsonProperty("outside_icon") @Nullable String outsideIcon,
        @JsonProperty("gift_id") int giftId,
        @JsonProperty("gift_type") int giftType,
        @JsonProperty("thum") @Nullable String thum,
        @JsonProperty("card_type") int cardType,
        @JsonProperty("thum_dark") @Nullable String thumDark,
        @JsonProperty("icon_dark") @Nullable String iconDark,
        @JsonProperty("outside_icon_dark") @Nullable String outsideIconDark,
        @JsonProperty("label_font_color") @Nullable String labelFontColor,
        @JsonProperty("thum_v2") @Nullable String thumV2,
        @JsonProperty("icon_v2") @Nullable String iconV2,
        @JsonProperty("room_name") @Nullable String roomName
    ) {}
}
