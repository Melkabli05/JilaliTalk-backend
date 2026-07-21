package com.jilali.roomcontext.infrastructure.dto.room;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record AudienceReconcileResponse(int revision, boolean changed, @Nullable List<RoomUser> list) {}
