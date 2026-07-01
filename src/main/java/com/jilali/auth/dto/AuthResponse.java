package com.jilali.auth.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AuthResponse(AuthUser user) {

    @Serdeable
    public record AuthUser(
        long userId,
        String nickname,
        String email,
        @Nullable String headUrl
    ) {}
}
