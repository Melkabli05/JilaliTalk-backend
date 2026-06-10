package com.jilali.signin.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ClaimRewardRequest(
    long host_id,
    String cname
) {}
