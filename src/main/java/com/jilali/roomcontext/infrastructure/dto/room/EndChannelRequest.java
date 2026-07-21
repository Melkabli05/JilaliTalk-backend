package com.jilali.roomcontext.infrastructure.dto.room;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record EndChannelRequest(@NotBlank String cname, @JsonProperty("ended_type") int endedType) {}
