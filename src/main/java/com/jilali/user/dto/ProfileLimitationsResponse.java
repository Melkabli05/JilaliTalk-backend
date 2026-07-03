package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from {@code GET /profile/v2/limitations}.
 * Uses {@code code}/{@code msg} envelope. Tells the client which profile fields are currently
 * editable and what caps/cooldowns apply — drives the edit-profile UI's disabled/greyed states.
 */
@Serdeable
public record ProfileLimitationsResponse(
    int code,
    String msg,
    @Nullable LimitationsData data
) {
    @Serdeable
    public record LimitationsData(
        @JsonProperty("tag_limit") @Nullable TagLimit tagLimit,
        @JsonProperty("lang_limit") @Nullable LangLimit langLimit,
        @JsonProperty("modify_nationality") boolean modifyNationality,
        @JsonProperty("modify_gender") boolean modifyGender,
        @JsonProperty("modify_birthday") boolean modifyBirthday,
        @JsonProperty("modify_birthday_by_admin") boolean modifyBirthdayByAdmin,
        @JsonProperty("is_modify_restricted") boolean isModifyRestricted
    ) {}

    /**
     * Max selectable tags per category. Field names deliberately mirror upstream's own
     * misspellings ({@code hobby_lmit}, not {@code hobby_limit}) — this is not a typo here.
     */
    @Serdeable
    public record TagLimit(
        @JsonProperty("hobby_lmit") @Nullable Integer hobbyLimit,
        @JsonProperty("travelling_lmit") @Nullable Integer travellingLimit,
        @JsonProperty("hometown_lmit") @Nullable Integer hometownLimit,
        @JsonProperty("education_lmit") @Nullable Integer educationLimit,
        @JsonProperty("occupation_lmit") @Nullable Integer occupationLimit,
        @JsonProperty("mbti_lmit") @Nullable Integer mbtiLimit,
        @JsonProperty("zodiac_sign_limit") @Nullable Integer zodiacSignLimit,
        @JsonProperty("blood_type_limit") @Nullable Integer bloodTypeLimit
    ) {}

    @Serdeable
    public record LangLimit(
        @JsonProperty("limit_days") @Nullable Integer limitDays,
        @JsonProperty("next_modify_ts") @Nullable Long nextModifyTs
    ) {}
}
