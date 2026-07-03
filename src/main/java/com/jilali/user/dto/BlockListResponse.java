package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Response from {@code GET /report_logic/v2/black/list}.
 * Uses {@code code}/{@code msg} envelope.
 * <p>
 * Every captured call returned an empty list, so the populated item shape is unconfirmed —
 * {@code blackList} is left as a raw map list rather than a typed record to avoid guessing field
 * names for a shape we've never actually observed. Type it properly once a populated response is
 * captured (block a test account, then re-capture).
 */
@Serdeable
public record BlockListResponse(
    int code,
    String msg,
    @Nullable BlockListData data
) {
    @Serdeable
    public record BlockListData(
        @JsonProperty("black_list") @Nullable List<Map<String, Object>> blackList
    ) {}
}
