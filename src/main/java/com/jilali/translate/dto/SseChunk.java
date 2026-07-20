package com.jilali.translate.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

/**
 * One {@code data: {...}} line from the AI translator's Server-Sent-Events response stream. The
 * actual chunk fields are nested under {@code data}; {@code code}/{@code message} are an
 * envelope-level status the translator ignores on success. {@link ChunkData#result} is the
 * per-chunk ciphertext — base64-encoded AES-256-ECB bytes under the Curve25519 session key, not
 * the plaintext. Multiple chunks arrive in order; each must be decrypted individually and
 * concatenated in arrival order to reconstruct the full translation.
 */
@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
public record SseChunk(
        int code,
        String message,
        ChunkData data
) {
    @Serdeable
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChunkData(
            String id,
            String object,
            String model,
            @JsonProperty("created_at") long createdAt,
            String result
    ) {}
}
