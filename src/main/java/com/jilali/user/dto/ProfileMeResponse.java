package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response from {@code GET /profile/v2/me}.
 * Uses {@code code}/@{code msg} envelope — the same format as LiveHub.
 */
@Serdeable
public record ProfileMeResponse(
    int code,
    String msg,
    @Nullable ProfileMeData data
) {
    @Serdeable
    public record ProfileMeData(
        @Nullable UserInfo user,
        @Nullable Increment increment,
        @Nullable VisitorData visitor
    ) {}

    @Serdeable
    public record UserInfo(
        @JsonProperty("user_id") long userId,
        @JsonProperty("nick_name") @Nullable String nickName,
        @JsonProperty("head_url") @Nullable String headUrl,
        @Nullable String nationality,
        @JsonProperty("vip_type") int vipType,
        int sex,
        @Nullable String email
    ) {}

    @Serdeable
    public record Increment(
        @JsonProperty("new_follower_count") int newFollowerCount,
        @JsonProperty("new_visitor_count") int newVisitorCount,
        @JsonProperty("new_profile_like_count") int newProfileLikeCount,
        @JsonProperty("new_profile_like_people") int newProfileLikePeople,
        @Nullable List<VisitorInfo> newVisitorInfos
    ) {}

    @Serdeable
    public record VisitorData(
        @Nullable List<VisitorInfo> recentVisitors
    ) {}

    @Serdeable
    public record VisitorInfo(
        @JsonProperty("user_id") long userId,
        @JsonProperty("head_url") @Nullable String headUrl,
        @Nullable String nationality
    ) {}
}