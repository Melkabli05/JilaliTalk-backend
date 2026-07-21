package com.jilali.roomcontext.infrastructure.dto.room;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Topic(long id, String name, @JsonProperty("category_id") long categoryId) {}
