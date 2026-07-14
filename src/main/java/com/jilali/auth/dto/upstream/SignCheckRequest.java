package com.jilali.auth.dto.upstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

/**
 * Body of {@code POST /user_register_center/v3/check} (ht/encbin) — the terminal signup step.
 * Matches smali {@code Ls21/i;} field-for-field. Returns only {@code verify_token} — never a
 * JWT, see {@link SignCheckResponse}.
 * <p>
 * {@code partyName}/{@code partyValue} are constructor parameters in the smali, but are never
 * serialized directly — those two fields carry no {@code @SerializedName} (confirmed: every
 * other field on this class has one), and the constructor instead repackages them into the
 * {@code third_party_login} field as {@code {partyName: partyValue}}, confirmed both from the
 * field-assignment bytecode in {@code s21/i;-><init>} and the real call site in
 * {@code j21/b.smali} (email signup: {@code partyName="email_password"},
 * {@code partyValue={email, password, email_verify_code}}). An earlier version of this class
 * emitted literal top-level {@code partyName}/{@code partyValue} JSON keys and always sent
 * {@code third_party_login:null} — backwards, caught by re-reading the smali constructor body
 * rather than trusting the endpoint's field-annotation list alone.
 */
@Serdeable
public record SignCheckRequest(
    @JsonProperty("login_type") int loginType,
    String email,
    String password,
    @JsonProperty("email_verify_code") String emailVerifyCode,
    int terminaltype,
    String version,
    @JsonProperty("client_lang") String clientLang,
    @JsonProperty("device_id") String deviceId,
    long t,
    String htntkey,
    String operator,
    @JsonProperty("sim_country_code") String simCountryCode,
    @JsonProperty("third_party_login") Map<String, PartyValue> thirdPartyLogin
) {
    private static final int EMAIL_LOGIN_TYPE = 1;
    private static final int ANDROID_TERMINAL_TYPE = 1;
    private static final String EMAIL_PASSWORD_PARTY = "email_password";

    @Serdeable
    public record PartyValue(String email, String password,
                              @JsonProperty("email_verify_code") String emailVerifyCode) {}

    public static SignCheckRequest forEmailSignup(String email, String password, String emailVerifyCode,
                                                   String version, String clientLang, String deviceId,
                                                   long t, String htntkey, String operator, String simCountryCode) {
        var partyValue = new PartyValue(email, password, emailVerifyCode);
        return new SignCheckRequest(
            EMAIL_LOGIN_TYPE, email, password, emailVerifyCode, ANDROID_TERMINAL_TYPE, version, clientLang,
            deviceId, t, htntkey, operator, simCountryCode, Map.of(EMAIL_PASSWORD_PARTY, partyValue));
    }
}
