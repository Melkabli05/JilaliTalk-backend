package com.jilali.roomcontext.infrastructure.dto.user;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

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
    @Nullable String liveStateCname,
    @Nullable List<String> tags,
    @Nullable UserInfoResponse.UserInfoItem details
) {
    public int pointsTotal() {
        if (details == null || details.points() == null) {
            return 0;
        }
        var p = details.points();
        return nvl(p.correct()) + nvl(p.translate()) + nvl(p.word())
             + nvl(p.speechToText()) + nvl(p.textTranslate()) + nvl(p.transliterate());
    }

    private static int nvl(@Nullable Integer v) {
        return v == null ? 0 : v;
    }

    public static String mapSex(int sex) {
        return switch (sex) {
            case 0 -> "female";
            case 1 -> "male";
            default -> "unknown";
        };
    }
}
