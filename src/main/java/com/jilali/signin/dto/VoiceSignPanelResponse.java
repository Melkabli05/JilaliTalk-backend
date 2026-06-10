package com.jilali.signin.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record VoiceSignPanelResponse(
    List<SignItem> sign_list,
    boolean to_day_signed,
    int consecutive_days
) {}
