package com.jilali.room.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record BatchQueryResponse(@Nullable List<BatchChannelStatus> items) {
    public List<BatchChannelStatus> items() {
        return items == null ? List.of() : items;
    }
}
