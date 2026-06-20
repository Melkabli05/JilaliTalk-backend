package com.jilali.user.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record VisitRequest(
    long uid,
    long visitorUid,
    String enter
) {}
