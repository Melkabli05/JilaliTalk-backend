package com.jilali.auth.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AuthResponse(AuthUserResponse user) {}
