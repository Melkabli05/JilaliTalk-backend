package com.jilali.roomcontext.infrastructure.dto.comment;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record CommentListResponse(
        @Nullable List<Comment> items,
        @JsonProperty("has_next") boolean hasNext,
        @JsonProperty("oldest_id") @Nullable String oldestId) {
    public List<Comment> items() {
        return items == null ? List.of() : items;
    }
}
