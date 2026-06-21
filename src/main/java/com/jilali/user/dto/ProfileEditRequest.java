package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for POST /profile/v1/modify_baseinfo.
 */
@Serdeable
public record ProfileEditRequest(
    @Nullable @JsonProperty("birthday") String birthday,
    @Nullable @JsonProperty("nationality") String nationality,
    @JsonProperty("os_type") int osType,
    @JsonProperty("version") String version
) {}
