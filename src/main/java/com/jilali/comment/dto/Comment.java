package com.jilali.comment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jilali.platform.time.Seconds;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Comment record used by both the upstream wire and the Angular frontend. The
 * previously-separate {@code CommentDto} (with millisecond timestamps) is gone — this single
 * record now exposes <b>epoch milliseconds</b> for {@code createdAt} / {@code updatedAt} (what
 * the frontend wants), with a {@link #fromWireSeconds(Comment) fromWireSeconds} factory that
 * re-scales the upstream's Unix-seconds values during deserialization.
 *
 * <p>The old 28-field {@code Comment} ↔ {@code CommentDto} duplicate is collapsed to this
 * single record; the two mappers that lived in {@code CommentController} and
 * {@code RoomJoinService} are deleted. The wire shape is unchanged: we still receive
 * {@code created_at} / {@code updated_at} in seconds and emit them in milliseconds — the
 * difference is just where the conversion happens.
 */
@Serdeable
public record Comment(
        @JsonProperty("_id") @Nullable String id,
        /** Epoch milliseconds, NOT seconds. Use {@link #fromWireSeconds(Comment)} for upstream values. */
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
        /** {@code expire_at} is a string-encoded Unix timestamp in the upstream payload
         *  (inconsistent with sibling fields); kept as a raw string for now. */
        @Nullable String expireAt,
        @JsonProperty("medal_wall_icon") @Nullable String medalWallIcon) {

    @Serdeable
    public record Msg(@Nullable Text text, @JsonProperty("reply_info") @Nullable ReplyInfo replyInfo) {
        @Serdeable
        public record Text(@Nullable String text) {
        }

        /** Mirrors SendCommentRequest.Msg.ReplyInfo / RoomRealtimeEvent.ReplyInfoEvent — same
         *  upstream reply-object shape on both the send and realtime-push paths. */
        @Serdeable
        public record ReplyInfo(
                @JsonProperty("msg_id") @Nullable String msgId,
                @JsonProperty("from_id") long fromId,
                @JsonProperty("from_nickname") @Nullable String fromNickname,
                @Nullable String text,
                @JsonProperty("msg_type") @Nullable String msgType) {
        }
    }

    /**
     * Builds a {@code Comment} with the upstream's Unix-seconds timestamps rescaled to
     * milliseconds (the unit this record now exposes). Used at the single wire-boundary
     * conversion point — controllers and services can then use {@code Comment} directly
     * without per-instance timestamp math.
     */
    public static Comment fromWireSeconds(Comment c) {
        return c.withTimestamps(Seconds.toMillis(c.createdAt()), Seconds.toMillis(c.updatedAt()));
    }

    /** Re-emits the record with different timestamp values (used by {@link #fromWireSeconds}). */
    public Comment withTimestamps(long createdAt, long updatedAt) {
        return new Comment(id, createdAt, updatedAt, cname, busiType, userId, nickname, headUrl,
                nationality, role, vipType, msg, dayRankLevel, giftLevel, fgLevel, fgName,
                fgIsActive, bubbleId, bubbleUrl, bubbleColor, hitBad, bubbleAnimalType,
                bubbleAnimalUrl, vipLogo, vipLogoAnim, expireAt, medalWallIcon);
    }
}
