package com.jilali.auth.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record SendEmailCodeRequest(@NotBlank @Email String email) {}
