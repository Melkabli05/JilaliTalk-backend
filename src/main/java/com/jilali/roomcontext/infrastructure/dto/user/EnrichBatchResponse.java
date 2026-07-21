package com.jilali.roomcontext.infrastructure.dto.user;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record EnrichBatchResponse(List<UserInfo> profiles) {}
