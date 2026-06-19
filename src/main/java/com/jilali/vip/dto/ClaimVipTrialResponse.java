package com.jilali.vip.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ClaimVipTrialResponse(boolean claimed) {}
