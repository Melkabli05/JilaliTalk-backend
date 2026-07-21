package com.jilali.roomcontext.infrastructure.dto.comment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jilali.platform.time.Seconds;
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
    public record Msg(@Nullable Text text, @JsonProperty("reply_info") @Nullable ReplyInfo replyInfo) {
        @Serdeable
        public record Text(@Nullable String text) {}

        @Serdeable
        public record ReplyInfo(
                @JsonProperty("msg_id") @Nullable String msgId,
                @JsonProperty("from_id") long fromId,
                @JsonProperty("from_nickname") @Nullable String fromNickname,
                @Nullable String text,
                @JsonProperty("msg_type") @Nullable String msgType) {}
    }

    public static Comment fromWireSeconds(Comment c) {
        return c.withTimestamps(Seconds.toMillis(c.createdAt()), Seconds.toMillis(c.updatedAt()));
    }

    public Comment withTimestamps(long createdAt, long updatedAt) {
        return new Comment(id, createdAt, updatedAt, cname, busiType, userId, nickname, headUrl,
                nationality, role, vipType, msg, dayRankLevel, giftLevel, fgLevel, fgName,
                fgIsActive, bubbleId, bubbleUrl, bubbleColor, hitBad, bubbleAnimalType,
                bubbleAnimalUrl, vipLogo, vipLogoAnim, expireAt, medalWallIcon);
    }
}
