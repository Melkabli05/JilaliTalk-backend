package com.jilali.auth.dto.upstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Response of {@code POST /user_register_center/v3/login}. Only the fields this BFF actually
 * consumes ({@code userId}, {@code jwt}) are modeled — the upstream payload carries
 * {@code area_code}, {@code reg_ts}, {@code is_adult}, {@code is_new_reg_user}, {@code is_vip}
 * inside {@code user_info}, but they're never read (per the audit's dead-code report).
 * Micronaut's global {@code FAIL_ON_UNKNOWN_PROPERTIES=false} lets the extra fields round-trip
 * through Jackson without breaking deserialization, so this is a strict removal of the
 * 5 unread record components.
 */
@Serdeable
public record LoginResponse(@JsonProperty("user_info") @Nullable UserInfo userInfo) {

    /**
     * Subset of HelloTalk's {@code user_info} we actually use: the numeric uid (the row key
     * for our local session table) and the live upstream JWT (carried in the cookie and
     * published via {@code AuthTokenHolder}). See {@code docs/audit/reports/dead-code.md}.
     */
    @Serdeable
    public record UserInfo(
        @JsonProperty("user_id") long userId,
        @Nullable String jwt
    ) {}
}
