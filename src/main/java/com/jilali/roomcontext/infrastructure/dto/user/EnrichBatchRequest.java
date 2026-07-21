package com.jilali.roomcontext.infrastructure.dto.user;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Serdeable
public record EnrichBatchRequest(@NotEmpty List<Long> userIds) {}
