package com.jilali.roomcontext.infrastructure.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record UnfollowResultResponse(int status, String message, @Nullable UnfollowResultData data) {
    @Serdeable
    public record UnfollowResultData(@JsonProperty("list_timestamp") long listTimestamp) {}
}
