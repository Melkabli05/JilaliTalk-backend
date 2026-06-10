package com.jilali.comment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record CommentNotify(
        long id,
        @JsonProperty("notify_type") int notifyType,
        @Nullable String label,
        @JsonProperty("text_color") @Nullable String textColor,
        @JsonProperty("background_color") @Nullable String backgroundColor,
        @JsonProperty("notify_content") @Nullable List<CommentNotifyContent> notifyContent) {
    public List<CommentNotifyContent> notifyContent() {
        return notifyContent == null ? List.of() : notifyContent;
    }
}
