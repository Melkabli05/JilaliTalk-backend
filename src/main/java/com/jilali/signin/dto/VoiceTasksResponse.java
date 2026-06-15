package com.jilali.signin.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

/**
 * Response shape for the {@code /voice/tasks} endpoint.
 * The upstream returns a list of task items as a flexible {@code List<Map<String, Object>>}.
 */
@Serdeable
public record VoiceTasksResponse(@Nullable List<Map<String, Object>> items) {
    public List<Map<String, Object>> items() {
        return items == null ? List.of() : items;
    }
}
