package com.jilali.platform.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Single source of truth for HelloTalk's wire-shape reward record.
 *
 * <p>Two previously-duplicated declarations existed — one in {@code signin/dto/RewardItem.java}
 * (non-nullable {@code multiName}) and one in {@code room/dto/RoomLevelConfigResponse.RewardItem}
 * (nullable {@code multiName}). Both are now consolidated here under the more permissive
 * nullable type, since the upstream wire payload genuinely may or may not carry the field
 * (per the smali for the relevant upstream-return class).
 *
 * <p>This is the first record to live in the new {@code com.jilali.platform.models} package,
 * the landing zone for cross-package shared records (per the target-structure report).
 * Both {@code signin} and {@code room} feature packages now import this single record
 * instead of redeclaring it.
 */
@Serdeable
public record RewardItem(
    int id,
    @JsonProperty("gift_id") int giftId,
    int type,
    @JsonProperty("card_type") int cardType,
    String name,
    int number,
    String icon,
    @JsonProperty("multi_name") @Nullable String multiName
) {}
