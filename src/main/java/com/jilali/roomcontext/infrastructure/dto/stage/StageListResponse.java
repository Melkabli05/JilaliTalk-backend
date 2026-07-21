package com.jilali.roomcontext.infrastructure.dto.stage;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record StageListResponse(
        @JsonProperty("is_host_in_room") boolean isHostInRoom,
        @Nullable List<StageMember> list) {
    public List<StageMember> list() {
        return list == null ? List.of() : list;
    }
}
