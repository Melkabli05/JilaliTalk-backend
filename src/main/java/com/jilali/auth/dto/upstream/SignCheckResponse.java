package com.jilali.auth.dto.upstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Response of {@code POST /user_register_center/v3/check} — only {@code verify_token} is
 * modeled; the partial {@code user_info}/{@code area_code}/{@code banned_info} it also returns
 * aren't needed since {@code com.jilali.auth.HelloTalkAuthService} always falls back into the
 * standard login pipeline (§7.1) to obtain the real JWT, rather than trying to assemble an
 * identity from this response. Confirmed from smali ({@code SignCheckResp}) to carry no
 * {@code jwt}/{@code access_token} field at all — that fallback isn't a simplification, it's required.
 */
@Serdeable
public record SignCheckResponse(@JsonProperty("verify_token") @Nullable String verifyToken) {}
