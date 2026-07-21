package com.jilali.roomcontext.infrastructure.dto.user;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@Serdeable
public record BlockListResponse(int code, String msg, @Nullable BlockListData data) {
    @Serdeable
    public record BlockListData(@JsonProperty("black_list") @Nullable List<Map<String, Object>> blackList) {}
}
