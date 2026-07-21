package com.jilali.roomcontext.infrastructure.dto.user;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

@Serdeable
public record LikeCountResponse(int status, String message, @Nullable LikeCountData data) {
    @Serdeable
    public record LikeCountData(
        @JsonProperty("unread_favor_count") int unreadFavorCount,
        @JsonProperty("unread_favor_people") int unreadFavorPeople
    ) {}
}
