package com.jilali.roomcontext.infrastructure.dto.manager;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ManagerJudgeResponse(@JsonProperty("is_online") boolean isOnline) {}
