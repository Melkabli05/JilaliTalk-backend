package com.jilali.roomcontext.infrastructure.dto.comment;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SendCommentResponse(long createdAtMs, @Nullable String id) {}
