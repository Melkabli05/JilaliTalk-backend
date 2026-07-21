package com.jilali.roomcontext.infrastructure.dto.vip;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ClaimVipTrialResponse(boolean claimed) {}
