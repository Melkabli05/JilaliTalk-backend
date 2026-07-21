# SignCheckRequest

`src/main/java/com/jilali/auth/dto/upstream/SignCheckRequest.java`

## Purpose
The wire body for `POST /user_register_center/v3/check` (ht/encbin) — the terminal signup step. Matches smali `Ls21/i;` field-for-field, including the subtle repackaging of `partyName`/`partyValue` into a `third_party_login` map — a shape corrected against an earlier version that emitted literal top-level `partyName`/`partyValue` keys.

## Responsibilities
- Represent the `/v3/check` request with exact wire names.
- Nest the email-signup third-party payload as `third_party_login: {"email_password": {email, password, email_verify_code}}` via the `PartyValue` record.
- Provide `forEmailSignup(...)` filling the login-type/terminal-type/party constants.

## Public API
Record `SignCheckRequest` components (wire names in parentheses):
- `int loginType (login_type)`, `String email`, `String password`, `String emailVerifyCode (email_verify_code)`, `int terminaltype`, `String version`, `String clientLang (client_lang)`, `String deviceId (device_id)`, `long t`, `String htntkey`, `String operator`, `String simCountryCode (sim_country_code)`, `Map<String, PartyValue> thirdPartyLogin (third_party_login)`.
- Nested `record PartyValue(String email, String password, String emailVerifyCode (email_verify_code))`.
- `static SignCheckRequest forEmailSignup(String email, String password, String emailVerifyCode, String version, String clientLang, String deviceId, long t, String htntkey, String operator, String simCountryCode)`.
- Private constants `EMAIL_LOGIN_TYPE=1`, `ANDROID_TERMINAL_TYPE=1`, `EMAIL_PASSWORD_PARTY="email_password"`.

## Dependencies
- `@JsonProperty`, `@Serdeable`, `java.util.Map`.
- Depended on BY: `HelloTalkAuthClientImpl.signupCheck` (via `forEmailSignup`); referenced by `SignCheckResponse` Javadoc. Grep-confirmed: `HelloTalkAuthClientImpl` (runtime), `SignCheckResponse` (doc).

## Coupling and cohesion analysis
High cohesion — one wire contract with a factory encapsulating both the constants and the non-obvious `third_party_login` map assembly. Low coupling. The `forEmailSignup` factory is exactly the right pattern (and a model for what `EmailLoginRequest` should adopt). The extensive smali-provenance Javadoc documenting the prior `partyName`/`partyValue` bug is exemplary.

## Code smells
- **Data Class:** appropriate.
- Mild **Long Parameter List** in `forEmailSignup` (10 params), but each is a genuinely distinct input (crypto/persona values), so it is acceptable and far safer than a positional record constructor.
- Duplicated payload fields: `email`/`password`/`emailVerifyCode` appear both at top level *and* inside `PartyValue`. This mirrors the wire (the real client sends both), so it is intentional fidelity, not a fixable smell.

## Technical debt
- None beyond the inherent brittleness of matching a reverse-engineered wire format.

## Duplicate logic
- The top-level `email`/`password`/`email_verify_code` vs the nested `PartyValue` copy is wire-mandated duplication, not code debt.
- Shares device-persona fields with the other upstream request DTOs — inherent.

## Dead or unused code
- None. `forEmailSignup`, `PartyValue`, constants, and accessors are all used/serialized.

## Refactoring recommendations
- None required — this file is the reference example for how upstream request DTOs should encapsulate their construction.
