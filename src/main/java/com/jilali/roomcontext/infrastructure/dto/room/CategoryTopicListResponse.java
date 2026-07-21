package com.jilali.roomcontext.infrastructure.dto.room;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record CategoryTopicListResponse(@Nullable List<Category> items) {
    public List<Category> items() {
        return items == null ? List.of() : items;
    }
}
