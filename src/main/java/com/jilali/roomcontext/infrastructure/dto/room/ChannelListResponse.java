package com.jilali.roomcontext.infrastructure.dto.room;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record ChannelListResponse(@Nullable List<ChannelListItem> items) {
    public List<ChannelListItem> items() {
        return items == null ? List.of() : items;
    }
}
