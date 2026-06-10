package com.jilali.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record UserBase(
        @Nullable String nickname,
        @Nullable String signature,
        @JsonProperty("head_url") @Nullable String headUrl,
        @Nullable String nationality,
        @JsonProperty("native_lang") int nativeLang,
        @JsonProperty("time_zone") long timeZone) {
}