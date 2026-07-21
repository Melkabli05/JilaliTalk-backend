package com.jilali.roomcontext.api.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record TranslateResponse(String translatedText) {}
