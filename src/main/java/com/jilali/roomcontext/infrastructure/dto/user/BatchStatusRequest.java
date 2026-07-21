package com.jilali.roomcontext.infrastructure.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Serdeable
public record BatchStatusRequest(@JsonProperty("user_ids") @NotEmpty List<Long> userIds) {}
