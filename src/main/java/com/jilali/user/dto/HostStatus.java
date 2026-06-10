package com.jilali.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record HostStatus(
        @JsonProperty("is_live_host") boolean isLiveHost,
        @JsonProperty("is_voice_room_host") boolean isVoiceRoomHost,
        @JsonProperty("is_banned_live") boolean isBannedLive,
        @JsonProperty("is_banned_voice_room") boolean isBannedVoiceRoom,
        @JsonProperty("is_minor") boolean isMinor) {
}
