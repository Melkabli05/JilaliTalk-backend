package com.jilali.room.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/** Wrapper for the {items:[...]} listing payloads. */
@Serdeable
public record ChannelListResponse(@Nullable List<ChannelListItem> items) {
    public List<ChannelListItem> items() {
        return items == null ? List.of() : items;
    }
}
