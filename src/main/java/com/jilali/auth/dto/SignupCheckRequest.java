package com.jilali.auth.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record SignupCheckRequest(
    @NotBlank @Email String email,
    @NotBlank String password,
    @NotBlank String emailVerifyCode
) {}
