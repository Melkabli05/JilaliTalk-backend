package com.jilali.roomcontext.infrastructure.dto.stage;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record PublisherTokenResponse(String token) {}
