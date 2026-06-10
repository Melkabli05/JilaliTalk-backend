package com.jilali.comment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Comment(
        @JsonProperty("_id") @Nullable String id,
        @JsonProperty("created_at") long createdAt,
        @JsonProperty("updated_at") long updatedAt,
        @JsonProperty("cname") @Nullable String cname,
        @JsonProperty("busi_type") int busiType,
        @JsonProperty("user_id") long userId,
        @Nullable String nickname,
        @JsonProperty("head_url") @Nullable String headUrl,
        @Nullable String nationality,
        int role,
        @JsonProperty("vip_type") int vipType,
        @Nullable Msg msg,
        @JsonProperty("day_rank_level") int dayRankLevel,
        @JsonProperty("gift_level") int giftLevel,
        @JsonProperty("fg_level") int fgLevel,
        @JsonProperty("fg_name") @Nullable String fgName,
        @JsonProperty("fg_is_active") boolean fgIsActive,
        @JsonProperty("bubble_id") int bubbleId,
        @JsonProperty("bubble_url") @Nullable String bubbleUrl,
        @JsonProperty("bubble_color") @Nullable String bubbleColor,
        @JsonProperty("hit_bad") int hitBad,
        @JsonProperty("bubble_animal_type") int bubbleAnimalType,
        @JsonProperty("bubble_animal_url") @Nullable String bubbleAnimalUrl,
        @JsonProperty("vip_logo") @Nullable String vipLogo,
        @JsonProperty("vip_logo_anim") @Nullable String vipLogoAnim,
        @Nullable String expireAt,
        @JsonProperty("medal_wall_icon") @Nullable String medalWallIcon) {

    @Serdeable
    public record Msg(@Nullable Text text) {
        @Serdeable
        public record Text(@Nullable String text) {
        }
    }
}