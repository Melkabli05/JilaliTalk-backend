package com.jilali.auth.dto.upstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Body of {@code POST /user_register_center/v3/pre_login} (bin/cc2018). Field shapes verified
 * against smali {@code Lsd/c;} — see {@code re_output/FINDINGS.md} §7.1.
 */
@Serdeable
public record EmailPreLoginRequest(
    @JsonProperty("login_type") int loginType,
    String email,
    @JsonProperty("os_type") int osType,
    @JsonProperty("device_id") String deviceId,
    @JsonProperty("client_version") String clientVersion,
    @JsonProperty("email_verify_code") @Nullable String emailVerifyCode
) {
    private static final int EMAIL_LOGIN_TYPE = 1;
    private static final int ANDROID_OS_TYPE = 1;

    public static EmailPreLoginRequest of(String email, String deviceId, String clientVersion) {
        return new EmailPreLoginRequest(EMAIL_LOGIN_TYPE, email, ANDROID_OS_TYPE, deviceId, clientVersion, null);
    }
}
