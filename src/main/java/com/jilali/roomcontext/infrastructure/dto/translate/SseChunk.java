package com.jilali.roomcontext.infrastructure.dto.translate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

/** One {@code data: {...}} line from the AI translator's SSE response stream. */
@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
public record SseChunk(int code, String message, ChunkData data) {
    @Serdeable
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChunkData(String id, String object, String model,
                             @JsonProperty("created_at") long createdAt, String result) {}
}
