package com.jilali.auth.dto.upstream;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Response envelope for the {@code /user_register_center} auth microservice:
 * {@code {"status":0,"msg":"success","data":{...}}}. Confirmed live against a real
 * {@code pre_login} response — an earlier version of {@link EmailPreLoginResponse}/
 * {@link LoginResponse} assumed the bare {@code data} shape was the whole response body,
 * which silently deserialized to null/zeroed fields (Jackson's default
 * {@code FAIL_ON_UNKNOWN_PROPERTIES=false} masked the mismatch instead of erroring) and
 * corrupted every downstream computation that depended on {@code cnonce}/{@code nonce}.
 * <p>
 * Distinct from {@code JilaliClient}'s {@code {code,msg,data}} envelope (different field name,
 * different microservice) — not reused from there deliberately.
 */
@Serdeable
public record HelloTalkEnvelope<T>(int status, @Nullable String msg, @Nullable T data) {

    public boolean isSuccess() {
        return status == 0;
    }
}
