package com.jilali.roomcontext.infrastructure.dto.signin;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

@Serdeable
public record VoiceTasksResponse(@Nullable List<Map<String, Object>> items) {
    public List<Map<String, Object>> items() {
        return items == null ? List.of() : items;
    }
}
