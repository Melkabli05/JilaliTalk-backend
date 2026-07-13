package com.jilali.translate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Upstream request shape for {@code POST translate/v2/ai_translator/translate}
 * (api-global.hellotalk8.com). Only {@code text} is ciphertext (AES-256-ECB under the
 * Curve25519-derived session key, base64-encoded) — the rest of the envelope is plain JSON.
 */
@Serdeable
public record AiTranslateUpstreamRequest(
        @JsonProperty("preferred_target_lang") String preferredTargetLang,
        @Nullable String context,
        String text,
        @JsonProperty("app_id") String appId,
        String model,
        boolean transliterate,
        @JsonProperty("fallback_target_lang") @Nullable String fallbackTargetLang
) {}
