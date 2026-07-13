package com.jilali.translate.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record TranslateRequest(
        @NotBlank String text,
        @NotBlank String targetLang
) {}
