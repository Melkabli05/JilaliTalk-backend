package com.jilali.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Response from {@code GET /livehub/user/status?user_id={id}}. Tells you where a user
 * currently is: {@code userStatusType} 0 = not in any room, 1 = hosting their own room
 * ({@code userId == hostId}, {@code cname} starts with {@code LS_}), 2 = in someone else's
 * room as a guest ({@code cname} starts with {@code VR_}). Verified against 58 captures in
 * {@code endpots/organized_captures_new/livehub_user_status_*.jsonl}. {@code roomId} is
 * always empty in every capture — {@code cname} is the real room identifier — but it's kept
 * since upstream still sends the key.
 * <p>
 * {@code hostId} is boxed ({@code Long}, not {@code long}) so a hypothetical upstream
 * null/missing doesn't fail the whole endpoint — captures always carry it, but the BFF is
 * the boundary that has to absorb upstream schema drift.
 */
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
