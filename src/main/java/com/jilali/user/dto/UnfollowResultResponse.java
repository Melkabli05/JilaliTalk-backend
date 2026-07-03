package com.jilali.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Response from {@code POST /relation/unfollow}. Verified live against the real
 * upstream (this call is absent from every endpots capture) — a successful unfollow
 * carries only {@code list_timestamp} in {@code data}, unlike {@code /relation/follow}'s
 * response, which also carries {@code status}/{@code limit_count}/{@code create_time}.
 * Kept as its own type rather than reusing {@link FollowResultResponse} so deserialization
 * never depends on fields upstream doesn't actually send here.
 */
@Serdeable
public record UnfollowResultResponse(
    int status,
    String message,
    @Nullable UnfollowResultData data
) {
    @Serdeable
    public record UnfollowResultData(
        @JsonProperty("list_timestamp") long listTimestamp
    ) {}
}
