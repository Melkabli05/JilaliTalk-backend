package com.jilali.comment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CommentNotifyContent(
        @Nullable String content,
        @JsonProperty("icon_type") int iconType,
        @JsonProperty("link_url") @Nullable String linkUrl,
        int level) {
}
