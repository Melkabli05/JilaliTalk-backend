package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for POST /user_profile_visitor/v2/my_history. Field names are explicit
 * {@code @JsonProperty} (this codebase doesn't configure a global naming strategy — see
 * {@code ProfileClient.VisitBody}'s doc for the same pattern); without them Serde emits
 * camelCase keys the real upstream silently ignores, confirmed against a live captured
 * request body: {@code {"device_type","client_ts","index","device_id","sign","client_ver",
 * "update_ts","client_os"}}.
 *
 * <p>{@code updateTs} is {@code long}, not {@code int} — the smali ({@code bq0/c.smali:958-1055})
 * sends the same corrected epoch-ms {@code client_ts} value in both fields. An {@code int} here
 * would silently overflow/wrap a real millisecond timestamp (current epoch-ms is already ~1.78
 * trillion, well past {@code Integer.MAX_VALUE}).
 */
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