package com.jilali.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record Category(
        long id,
        String name,
        @JsonProperty("bg_color") @Nullable String bgColor,
        @JsonProperty("font_color") @Nullable String fontColor,
        @Nullable List<Topic> topics) {
    public List<Topic> topics() {
        return topics == null ? List.of() : topics;
    }
}
