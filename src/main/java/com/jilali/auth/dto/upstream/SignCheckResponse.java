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
public record SignCheckResponse(
    @JsonProperty("verify_token") @Nullable String verifyToken,
    @JsonProperty("user_info") @Nullable UserInfo userInfo
) {
    /**
     * Subset of HelloTalk's {@code user_info} the BFF actually consumes after
     * re-reading {@code re_output/apktool_out/smali_classes22/com/hellotalk/sign/register/data/SignCheckResp$UserInfoBean.smali}
     * and the per-field assignments at j21/b.smali:353. {@code bind_id} is the per-attempt
     * identifier upstream issues in /v3/check responses and is what the smali's SignProfileV2Activity
     * then threads into /v3/reg/prepare — not the device id (confirmed by the live capture:
     * the static device_id gets rejected with status=100 "invalid bind_id").
     */
    @Serdeable
    public record UserInfo(@JsonProperty("bind_id") @Nullable String bindId) {}
}
