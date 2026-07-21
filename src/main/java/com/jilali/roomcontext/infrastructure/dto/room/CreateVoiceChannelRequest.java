package com.jilali.roomcontext.infrastructure.dto.room;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Serdeable
public record CreateVoiceChannelRequest(
        @JsonProperty("visible_status") int visibleStatus,
        @NotBlank String name,
        @JsonProperty("lang_id") @Positive int langId,
        @JsonProperty("category_id_v2") @Nullable Long categoryIdV2,
        @JsonProperty("topic_id_v2") @Nullable Long topicIdV2,
        @Nullable String notice,
        @JsonProperty("game_type") @Nullable Integer gameType) {
}
