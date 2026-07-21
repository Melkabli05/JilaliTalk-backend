package com.jilali.roomcontext.infrastructure.dto.user;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

@Serdeable
public record VisitorHistoryRequest(
    @JsonProperty("device_type") @Nullable String deviceType,
    @JsonProperty("client_ts") @Nullable Long clientTs,
    int index,
    @JsonProperty("device_id") @Nullable String deviceId,
    @Nullable String sign,
    @JsonProperty("client_ver") @Nullable String clientVer,
    @JsonProperty("update_ts") long updateTs,
    @JsonProperty("client_os") int clientOs
) {}
