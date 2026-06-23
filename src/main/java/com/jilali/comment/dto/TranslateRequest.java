package com.jilali.comment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record TranslateRequest(
        @NotBlank String text,
        @JsonProperty("preferred_target_lang") @NotBlank String preferredTargetLang,
        @Nullable @JsonProperty("context") String context,
        @JsonProperty("app_id") String appId,
        String model,
        @JsonProperty("transliterate") boolean transliterate,
        @Nullable @JsonProperty("fallback_target_lang") String fallbackTargetLang) {

    public static TranslateRequest of(String text, String targetLang) {
        return new TranslateRequest(
                text,
                targetLang,
                null,
                "HelloTalk",
                "qwen_mt_plus",
                false,
                null);
    }
}
