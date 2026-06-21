package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from {@code POST /relation/follow}.
 * Uses {@code status}/{@code message} envelope with a nested {@code data} object.
 */
@Serdeable
public record FollowResultResponse(
    int status,
    String message,
    @Nullable FollowResultData data
) {
    @Serdeable
    public record FollowResultData(
        @JsonProperty("list_timestamp") long listTimestamp,
        int status,
        int limitCount,
        long createTime
    ) {}
}