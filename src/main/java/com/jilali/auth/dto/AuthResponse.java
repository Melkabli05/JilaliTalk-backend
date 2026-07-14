package com.jilali.auth.dto;

import io.micronaut.serde.annotation.Serdeable;

/** Matches the Angular frontend's {@code AuthResponse} (`core/auth/auth.service.ts`):
 *  {@code {user: AuthUser}}, not a bare {@link AuthUserResponse}. */
@Serdeable
public record AuthResponse(AuthUserResponse user) {}
