package com.jilali.comment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Comment list DTO — server-side variant with {@code created_at_ms} / {@code updated_at_ms}
 * in milliseconds (Unix seconds × 1000). Used by the {@code GET /api/comments} endpoint so the
 * frontend receives JS-compatible timestamps without any {@code map()} transform.
 */
@Serdeable
public record CommentListDto(
        @Nullable List<CommentDto> items,
        @JsonProperty("has_next") boolean hasNext,
        @JsonProperty("oldest_id") @Nullable String oldestId) {

    public List<CommentDto> items() {
        return items == null ? List.of() : items;
    }
}
