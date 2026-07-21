package com.jilali.roomcontext.infrastructure.dto.comment;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record CaptionHistoryResponse(
        @Nullable List<CaptionEntry> list,
        @JsonProperty("has_more") boolean hasMore) {
    public List<CaptionEntry> list() {
        return list == null ? List.of() : list;
    }
}
