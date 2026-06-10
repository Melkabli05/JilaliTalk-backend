package com.jilali.room.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Serdeable
public record BatchQueryRequest(@NotEmpty List<String> cnames) {
}
