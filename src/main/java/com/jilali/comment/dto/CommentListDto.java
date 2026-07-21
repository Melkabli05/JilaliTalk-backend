package com.jilali.comment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Comment list DTO returned by the {@code GET /api/comments} endpoint. Carries the same
 * {@code Comment} record the upstream returns, with timestamps already rescaled to milliseconds
 * via {@link Comment#fromWireSeconds(Comment) Comment.fromWireSeconds}.
 */
@Serdeable
public record CommentListDto(
        @Nullable List<Comment> items,
        @JsonProperty("has_next") boolean hasNext,
        @JsonProperty("oldest_id") @Nullable String oldestId) {

    public List<Comment> items() {
        return items == null ? List.of() : items;
    }
}
