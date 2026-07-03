package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from {@code GET /config-center/v1/user_tags}.
 * Static reference/config data — the full catalog of selectable interest/identity tags shown
 * when editing a profile. Backs the caps returned by {@link ProfileLimitationsResponse}.
 * <p>
 * Only 5 of the 8 tag categories {@code profile/v2/limitations} defines caps for are present
 * here (hobby, occupation, mbti, constellation, blood_type) — {@code travelling}, {@code
 * hometown}, and {@code education} are served by some other, uncaptured call.
 */
@Serdeable
public record UserTagsResponse(
    int code,
    int status,
    String msg,
    @JsonProperty("md5_sum") @Nullable String md5Sum,
    @Nullable UserTagsData data
) {
    @Serdeable
    public record UserTagsData(
        @Nullable List<TagGroup> hobby,
        @Nullable List<TagGroup> occupation,
        @Nullable List<TagGroup> mbti,
        @Nullable List<TagGroup> constellation,
        @JsonProperty("blood_type") @Nullable List<TagGroup> bloodType
    ) {}

    @Serdeable
    public record TagGroup(
        @Nullable Integer id,
        @JsonProperty("category_title") @Nullable String categoryTitle,
        @Nullable List<TagChild> children
    ) {}

    @Serdeable
    public record TagChild(
        @Nullable Integer id,
        @Nullable String name
    ) {}
}
