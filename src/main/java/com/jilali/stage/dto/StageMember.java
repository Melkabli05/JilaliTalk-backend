package com.jilali.stage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record StageMember(
        @JsonProperty("user_id") long userId,
        String nickname,
        @JsonProperty("head_url") @Nullable String headUrl,
        @Nullable String nationality,
        int role,
        @JsonProperty("is_turn_on_mic") boolean isTurnOnMic,
        @JsonProperty("is_turn_on_cam") boolean isTurnOnCam) {
}
