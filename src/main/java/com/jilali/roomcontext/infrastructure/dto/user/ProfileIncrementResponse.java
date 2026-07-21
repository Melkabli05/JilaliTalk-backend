package com.jilali.roomcontext.infrastructure.dto.user;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ProfileIncrementResponse(int status, String message, @Nullable ProfileMeResponse.Increment data) {}
