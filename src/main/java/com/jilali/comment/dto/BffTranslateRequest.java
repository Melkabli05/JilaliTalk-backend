package com.jilali.comment.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record BffTranslateRequest(
        @NotBlank String text,
        @NotBlank String targetLang) {
}
