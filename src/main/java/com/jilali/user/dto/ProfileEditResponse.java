package com.jilali.user.dto;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Response from POST /profile/v1/modify_baseinfo.
 */
@Serdeable
public record ProfileEditResponse(
    int status,
    String msg
) {}
