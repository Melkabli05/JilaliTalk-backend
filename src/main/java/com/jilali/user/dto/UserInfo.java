package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Clean user info DTO returned to frontend clients.
 * Derived from the encrypted HelloTalk userinfo response.
 */
@Serdeable
public record UserInfo(
    long userId,
    @Nullable String username,
    @Nullable String nickname,
    @Nullable String birthday,
    @Nullable String accountType,
    @Nullable String fullPy,
    @Nullable Integer age,
    @Nullable String sex,
    @Nullable String nationality,
    @Nullable String city,
    @Nullable String fullCountry,
    @Nullable String areaCode,
    @Nullable Integer regDays,
    @Nullable String liveStateCname
) {
    /** Maps HelloTalk's numeric sex to labels. */
    public static String mapSex(int sex) {
        return switch (sex) {
            case 0 -> "female";
            case 1 -> "male";
            default -> "unknown";
        };
    }
}