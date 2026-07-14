package com.jilali.auth.dto.upstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Response of {@code POST /user_register_center/v3/pre_login} — {@code cnonce}/{@code nonce}
 * are the per-request salts {@link com.jilali.crypto.Md5Util#emailPasswordHash} needs.
 */
@Serdeable
public record EmailPreLoginResponse(
    @JsonProperty("user_id") long userId,
    String cnonce,
    String nonce
) {}
