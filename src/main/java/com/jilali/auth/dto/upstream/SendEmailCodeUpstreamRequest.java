package com.jilali.auth.dto.upstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Body of {@code POST /user_register_center/v3/send_email_code} (ht/encbin), matching smali
 * {@code Ls21/f;}: {@code {behavior_validate, email, scene}}. {@code scene}'s literal value is
 * confirmed from {@code s21/f;}'s only constructor, which hardcodes it — there is no other
 * overload and no other construction call site for this class, so this is the value for every
 * caller, not just signup (despite the name suggesting a registration-specific variant).
 */
@Serdeable
public record SendEmailCodeUpstreamRequest(
    @JsonProperty("behavior_validate") String behaviorValidate,
    String email,
    String scene
) {
    public static final String NEW_DEVICE_LOGIN_SCENE = "new_device_login";

    public static SendEmailCodeUpstreamRequest forSignup(String email) {
        return new SendEmailCodeUpstreamRequest("", email, NEW_DEVICE_LOGIN_SCENE);
    }
}
