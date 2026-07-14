package com.jilali.auth.dto.upstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Response of {@code POST /user_register_center/v3/login}. Only {@code userInfo} is modeled —
 * {@code pre_register_info}/{@code countdown_info}/{@code switch_config}/{@code banned_info}
 * are ignored (global {@code FAIL_ON_UNKNOWN_PROPERTIES=false}) since nothing in this feature
 * needs them yet; see {@code re_output/FINDINGS.md} §7.1 if a future caller does.
 */
@Serdeable
public record LoginResponse(@JsonProperty("user_info") @Nullable UserInfo userInfo) {

    /** {@code jwt} is the access token every subsequent upstream call authenticates with. */
    @Serdeable
    public record UserInfo(
        @JsonProperty("user_id") long userId,
        @Nullable String jwt,
        @JsonProperty("area_code") @Nullable String areaCode,
        @JsonProperty("reg_ts") long regTs,
        @JsonProperty("is_adult") int isAdult,
        @JsonProperty("is_new_reg_user") boolean isNewRegUser,
        @JsonProperty("is_vip") boolean isVip
    ) {}
}
