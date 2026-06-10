package com.jilali.manager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ManagerJudgeResponse(@JsonProperty("is_online") boolean isOnline) {
}
