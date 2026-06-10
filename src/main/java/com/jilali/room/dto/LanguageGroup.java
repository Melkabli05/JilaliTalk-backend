package com.jilali.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/** Element of the bare-array payload from language_group/{voice,live}. */
@Serdeable
public record LanguageGroup(
        @JsonProperty("lang_id") int langId,
        @Nullable List<Integer> langs,
        @JsonProperty("langs_len") int langsLen) {
}
