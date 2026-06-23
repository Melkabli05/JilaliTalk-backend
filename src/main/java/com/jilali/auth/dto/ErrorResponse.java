package com.jilali.auth.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ErrorResponse(String message) {}
