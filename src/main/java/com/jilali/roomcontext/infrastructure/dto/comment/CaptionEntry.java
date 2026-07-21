package com.jilali.roomcontext.infrastructure.dto.comment;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CaptionEntry(
        @JsonProperty("_id") String id,
        @JsonProperty("user_id") long userId,
        @JsonProperty("nick_name") String nickName,
        @Nullable String nationality,
        String text,
        @JsonProperty("create_at") long createAt) {
}
