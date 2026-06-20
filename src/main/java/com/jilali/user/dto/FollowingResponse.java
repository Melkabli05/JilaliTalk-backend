package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@Serdeable
public record FollowingResponse(
    int status,
    String message,
    @Nullable FollowingData data
) {
    @Serdeable
    public record FollowingData(
        String pageIndex,
        boolean more,
        int count,
        @Nullable PinnedStat pinnedStat,
        @Nullable List<FollowerUser> list
    ) {}

    @Serdeable
    public record PinnedStat(int limit, int cnt) {}

    @Serdeable
    public record FollowerUser(
        @JsonProperty("user_id") long userId,
        @Nullable String sex,
        @Nullable String nationality,
        @JsonProperty("head_url") @Nullable String headUrl,
        @JsonProperty("nick_name") @Nullable String nickName,
        @Nullable Integer vipType,
        int giftLevel,
        @Nullable String remarkName,
        @JsonProperty("is_mutual") boolean isMutual
    ) {}
}
