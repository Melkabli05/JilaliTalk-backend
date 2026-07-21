package com.jilali.roomcontext.infrastructure.dto.user;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

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
    public record TagChild(@Nullable Integer id, @Nullable String name) {}
}
