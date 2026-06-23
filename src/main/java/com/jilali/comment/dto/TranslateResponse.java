package com.jilali.comment.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record TranslateResponse(String text) {
}
