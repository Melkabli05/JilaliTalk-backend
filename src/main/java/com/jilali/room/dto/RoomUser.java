package com.jilali.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record RoomUser(
        @JsonProperty("user_id") long userId,
        String nickname,
        @JsonProperty("head_url") @Nullable String headUrl,
        @Nullable String nationality,
        @JsonProperty("cname") @Nullable String cname,
        @JsonProperty("is_in_room") boolean isInRoom,
        @JsonProperty("is_on_mic") boolean isOnMic,
        @JsonProperty("is_raise_hand") boolean isRaiseHand,
        @JsonProperty("is_turn_on_mic") boolean isTurnOnMic,
        @JsonProperty("is_turn_on_cam") boolean isTurnOnCam,
        int role,
        @JsonProperty("busi_type") int busiType,
        @JsonProperty("is_banned_comment") boolean isBannedComment,
        @JsonProperty("is_banned_mic") boolean isBannedMic,
        @JsonProperty("daily_cost_coins") int dailyCostCoins,
        @JsonProperty("gift_level") int giftLevel,
        @JsonProperty("vip_type") int vipType,
        @JsonProperty("fg_level") int fgLevel,
        @JsonProperty("fg_name") @Nullable String fgName,
        @JsonProperty("fg_is_active") boolean fgIsActive,
        @Nullable UserBase base) {

    public RoomUser {
        if (fgName == null) fgName = "";
        if (nickname == null) nickname = "";
    }
}