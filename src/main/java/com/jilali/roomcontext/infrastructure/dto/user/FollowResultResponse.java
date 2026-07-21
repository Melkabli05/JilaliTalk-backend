package com.jilali.roomcontext.infrastructure.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record FollowResultResponse(int status, String message, @Nullable FollowResultData data) {
    @Serdeable
    public record FollowResultData(
        @JsonProperty("list_timestamp") long listTimestamp,
        int status,
        int limitCount,
        long createTime
    ) {}
}
