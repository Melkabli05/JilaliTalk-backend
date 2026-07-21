package com.jilali.roomcontext.infrastructure.dto.room;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CreateVoiceChannelResponse(String cname, String token, @JsonProperty("rtc_engine") int rtcEngine) {}
