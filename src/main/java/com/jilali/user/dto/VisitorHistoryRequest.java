package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for POST /user_profile_visitor/v2/my_history.
 * Forwarded from the frontend device client fields.
 */
@Serdeable
public record VisitorHistoryRequest(
    @Nullable String deviceType,
    @Nullable Long clientTs,
    int index,
    @Nullable String deviceId,
    @Nullable String sign,
    @Nullable String clientVer,
    int updateTs,
    int clientOs
) {}