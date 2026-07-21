package com.jilali.roomcontext.infrastructure.dto.user;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@Serdeable
public record FollowersResponse(int status, String message, @Nullable FollowersData data) {
    @Serdeable
    public record FollowersData(
        @JsonProperty("page_index") String pageIndex,
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
        int sex,
        @Nullable String nationality,
        @JsonProperty("head_url") @Nullable String headUrl,
        @JsonProperty("nick_name") @Nullable String nickName,
        @JsonProperty("native_lang") @Nullable Integer nativeLang,
        @Nullable Integer vipType,
        int giftLevel,
        @Nullable String remarkName,
        @JsonProperty("is_mutual") boolean isMutual
    ) {}
}
