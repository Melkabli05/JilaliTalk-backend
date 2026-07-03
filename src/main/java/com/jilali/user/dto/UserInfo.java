package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Clean user info DTO returned to frontend clients.
 * Derived from the encrypted HelloTalk userinfo response.
 * <p>
 * {@code details} carries the full upstream profile (points, tags, location,
 * relation, privileges, online/live state, vip info, language pairs, etc.)
 * for clients that need more than the flattened convenience fields above.
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
    @Nullable String liveStateCname,
    /** Pre-flattened tag list: all non-null {@code .tag} strings from the 8 upstream tag arrays. */
    @Nullable List<String> tags,
    @Nullable UserInfoResponse.UserInfoItem details
) {

    /**
     * Pre-computed total of the six user-contribution point categories (correct, translate, word,
     * speechToText, textTranslate, transliterate). Matches the sum computed client-side in
     * {@code user-info-modal.component.ts pointsSummary}. Pass-through endpoints that call this
     * field directly eliminate duplicate arithmetic logic between the two codebases.
     */
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
    /** Maps HelloTalk's numeric sex to labels. */
    public static String mapSex(int sex) {
        return switch (sex) {
            case 0 -> "female";
            case 1 -> "male";
            default -> "unknown";
        };
    }
}