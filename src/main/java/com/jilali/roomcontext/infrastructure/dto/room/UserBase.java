package com.jilali.roomcontext.infrastructure.dto.room;

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
        @JsonProperty("teach_lang_2") int teachLang2,
        @JsonProperty("teach_lang_2_level") int teachLang2Level,
        @JsonProperty("teach_lang_3") int teachLang3,
        @JsonProperty("teach_lang_3_level") int teachLang3Level,
        @JsonProperty("learn_lang_1") int learnLang1,
        @JsonProperty("learn_lang_1_level") int learnLang1Level,
        @JsonProperty("learn_lang_2") int learnLang2,
        @JsonProperty("learn_lang_2_level") int learnLang2Level,
        @JsonProperty("learn_lang_3") int learnLang3,
        @JsonProperty("learn_lang_3_level") int learnLang3Level,
        @JsonProperty("learn_lang_4") int learnLang4,
        @JsonProperty("learn_lang_4_level") int learnLang4Level,
        @JsonProperty("learn_lang_5") int learnLang5,
        @JsonProperty("learn_lang_5_level") int learnLang5Level,
        @JsonProperty("time_zone") long timeZone) {
}
