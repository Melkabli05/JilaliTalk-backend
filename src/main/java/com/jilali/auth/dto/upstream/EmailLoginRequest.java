package com.jilali.auth.dto.upstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Body of {@code POST /user_register_center/v3/login} for the email/password variant
 * (bin/cc2018). Combines the {@code Lrd/a} device-fingerprint base fields (top-level) with the
 * email-login extras, which are packed into a nested {@code account_login} object — confirmed
 * from smali {@code sd/b;-><init>}, which builds a {@code Map} of exactly {@code user_id}/
 * {@code passwd}/{@code email_verify_code} and assigns it to the one field {@code sd/b}
 * declares beyond its {@code Lrd/a} parent, annotated {@code @SerializedName("account_login")}.
 * An earlier version of this class flattened those three fields onto the top level — wrong,
 * caught by re-reading the smali directly rather than trusting the prose summary in
 * {@code re_output/FINDINGS.md} §7.1.
 * <p>
 * {@code behaviorValidate} is {@code @Nullable} deliberately: {@code rd/a.smali}'s field {@code w}
 * has no {@code @SerializedName}-adjacent default and is only ever assigned via its setter,
 * which the real call site ({@code qd/b.smali}) only invokes when
 * {@code !TextUtils.isEmpty(behaviorValidate)} — when there's no captcha/risk data, the setter
 * is skipped entirely, the field stays at its default ({@code null}, never touched in the
 * constructor), and Gson (which omits nulls by default, unlike Jackson) never serializes the
 * key at all. Sending it as {@code ""} (present, empty) instead of omitting it — an earlier
 * version of this class did that — is a real, confirmed difference from the wire format a real
 * client produces.
 * <p>
 * {@code androidApkSignature} is {@code @Nullable} and omitted entirely when impersonating iOS
 * (see {@link com.jilali.auth.HelloTalkAuthClientImpl}) — every real captured
 * {@code /v3/login} request in this project's reference traffic is from an iOS device and has
 * no such field at all (it's Android-only, computed via {@code TeaUtils.xConnInfo}, a native
 * routine this project has never verified end-to-end against a live server — its own
 * implementation docstring already flags that gap). Since there is zero real Android traffic to
 * compare against, and the iOS shape is fully verified live, this BFF impersonates iOS for login
 * to avoid staking success on an unverifiable signature.
 */
@Serdeable
public record EmailLoginRequest(
    @JsonProperty("mobile_operator") String mobileOperator,
    @JsonProperty("operator_country") String operatorCountry,
    @JsonProperty("login_type") int loginType,
    String source,
    @JsonProperty("os_type") int osType,
    long ts,
    @JsonProperty("android_apk_signature") @Nullable String androidApkSignature,
    @JsonProperty("device_id") String deviceId,
    @JsonProperty("client_version") String clientVersion,
    @JsonProperty("client_version_num") int clientVersionNum,
    @JsonProperty("os_version") String osVersion,
    @JsonProperty("os_lang") String osLang,
    @JsonProperty("client_lang") String clientLang,
    @JsonProperty("device_detail") String deviceDetail,
    @JsonProperty("appstore_country") String appstoreCountry,
    String sign,
    @JsonProperty("watchman_token") String watchmanToken,
    @JsonProperty("jail_break") int jailBreak,
    @JsonProperty("net_type") int netType,
    @JsonProperty("is_vpn") int isVpn,
    @JsonProperty("behavior_validate") @Nullable String behaviorValidate,
    @JsonProperty("irisk_token") String iriskToken,
    @JsonProperty("account_login") AccountLogin accountLogin
) {
    /** The {@code account_login} nested object — confirmed field set from {@code sd/b.smali}. */
    @Serdeable
    public record AccountLogin(
        @JsonProperty("user_id") long userId,
        String passwd,
        @JsonProperty("email_verify_code") @Nullable String emailVerifyCode
    ) {}
}
