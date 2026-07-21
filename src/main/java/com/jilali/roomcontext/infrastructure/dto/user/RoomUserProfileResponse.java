package com.jilali.roomcontext.infrastructure.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomUserProfileResponse(
    int code,
    @Nullable String msg,
    @Nullable Data data
) {
    @Serdeable
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@JsonProperty("follow_stat") @Nullable FollowStat followStat) {}

    @Serdeable
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FollowStat(int status, @JsonProperty("folower_status") int folowerStatus) {}
}
