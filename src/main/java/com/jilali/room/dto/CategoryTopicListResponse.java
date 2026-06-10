package com.jilali.room.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record CategoryTopicListResponse(@Nullable List<Category> items) {
    public List<Category> items() {
        return items == null ? List.of() : items;
    }
}
