package com.jilali.user.dto;

import com.jilali.room.dto.RoomUser;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record RoomUserListResponse(
        @Nullable List<RoomUser> list,
        @JsonProperty("audience_total") int audienceTotal) {
    public List<RoomUser> list() {
        return list == null ? List.of() : list;
    }
}
