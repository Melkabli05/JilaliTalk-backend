package com.jilali.roomcontext.infrastructure.dto.user;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ProfileEditResponse(int status, String msg) {}
