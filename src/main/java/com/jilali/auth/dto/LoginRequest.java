package com.jilali.auth.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
