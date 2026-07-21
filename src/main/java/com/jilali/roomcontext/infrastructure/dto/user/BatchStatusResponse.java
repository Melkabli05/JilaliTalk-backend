package com.jilali.roomcontext.infrastructure.dto.user;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record BatchStatusResponse(
        @Nullable List<UserOnlineStatus> list,
        @Nullable List<UserOnlineStatus> others) {
    public List<UserOnlineStatus> others() {
        return others == null ? List.of() : others;
    }
}
