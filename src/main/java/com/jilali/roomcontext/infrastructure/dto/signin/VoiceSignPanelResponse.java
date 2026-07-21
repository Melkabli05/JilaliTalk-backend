package com.jilali.roomcontext.infrastructure.dto.signin;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record VoiceSignPanelResponse(
    @JsonProperty("sign_list") List<SignItem> signList,
    @JsonProperty("to_day_signed") boolean toDaySigned,
    @JsonProperty("consecutive_days") int consecutiveDays
) {}
