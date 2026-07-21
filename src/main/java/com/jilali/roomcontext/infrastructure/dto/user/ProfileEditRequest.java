package com.jilali.roomcontext.infrastructure.dto.user;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

@Serdeable
public record ProfileEditRequest(
    @Nullable @JsonProperty("birthday") String birthday,
    @Nullable @JsonProperty("nationality") String nationality,
    @Nullable @JsonProperty("learn_lang2") Integer learnLang2,
    @Nullable @JsonProperty("learn_lang2_level") Integer learnLang2Level,
    @Nullable @JsonProperty("teach_lang2") Integer teachLang2,
    @Nullable @JsonProperty("teach_lang2_level") Integer teachLang2Level,
    @JsonProperty("os_type") int osType,
    @JsonProperty("version") String version
) {}
