package com.jilali.translate.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record TranslateResponse(String translatedText) {}
