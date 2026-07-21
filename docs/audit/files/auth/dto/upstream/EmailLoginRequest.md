# EmailLoginRequest

`src/main/java/com/jilali/auth/dto/upstream/EmailLoginRequest.java`

## Purpose
The wire body for `POST /user_register_center/v3/login` (bin/cc2018 codec), email/password variant. It combines the `Lrd/a` device-fingerprint base fields (top-level) with the email-login extras packed into a nested `account_login` object — a shape reverse-engineered from HelloTalk smali (`sd/b;-><init>`) and corrected against an earlier flattened version.

## Responsibilities
- Represent, field-for-field and with exact `@JsonProperty` wire names, the login request HelloTalk expects.
- Nest `user_id`/`passwd`/`email_verify_code` under `account_login` (record `AccountLogin`), matching the real client.
- Model `behaviorValidate` and `androidApkSignature` as `@Nullable` so they are omitted (Gson-parity via the client's null-omitting `ObjectMapper`) when impersonating iOS or when no captcha/risk data exists.

## Public API
Record `EmailLoginRequest` components (all `@Serdeable`/Jackson-serialized; wire names in parentheses):
- `String mobileOperator (mobile_operator)`, `String operatorCountry (operator_country)`, `int loginType (login_type)`, `String source`, `int osType (os_type)`, `long ts`, `@Nullable String androidApkSignature (android_apk_signature)`, `String deviceId (device_id)`, `String clientVersion (client_version)`, `int clientVersionNum (client_version_num)`, `String osVersion (os_version)`, `String osLang (os_lang)`, `String clientLang (client_lang)`, `String deviceDetail (device_detail)`, `String appstoreCountry (appstore_country)`, `String sign`, `String watchmanToken (watchman_token)`, `int jailBreak (jail_break)`, `int netType (net_type)`, `int isVpn (is_vpn)`, `@Nullable String behaviorValidate (behavior_validate)`, `String iriskToken (irisk_token)`, `AccountLogin accountLogin (account_login)`.
- Nested `record AccountLogin(long userId (user_id), String passwd, @Nullable String emailVerifyCode (email_verify_code))`.

## Dependencies
- `@JsonProperty`, `@Nullable`, `@Serdeable`.
- Depended on BY: `HelloTalkAuthClientImpl.performLogin` (constructs it positionally). Grep-confirmed: `HelloTalkAuthClientImpl` only.

## Coupling and cohesion analysis
High cohesion — one wire contract. Coupling is limited to serialization annotations. The design correctly isolates the upstream wire shape from the BFF-facing DTOs. The extensive Javadoc documenting smali provenance and the two prior bugs (flattened `account_login`, empty-string vs omitted `behavior_validate`) is exemplary reverse-engineering discipline.

## Code smells
- **Data Class with a very Long Parameter List (23 top-level components + 3 nested):** the record itself is fine, but it forces a 23-argument positional construction in `HelloTalkAuthClientImpl.performLogin` — the real smell lives at that call site (already flagged as a Long Method there), not in this DTO. A `forEmailLogin(...)` static factory here (mirroring `SignCheckRequest.forEmailSignup` and `EmailPreLoginRequest.of`) would move the assembly into the type and eliminate the positional-argument hazard.
- **Primitive Obsession:** booleans-as-ints (`jailBreak`, `isVpn`, `osType`, `loginType`) mirror the wire, so this is unavoidable fidelity, not a fixable smell.

## Technical debt
- No static factory (unlike its sibling upstream DTOs), so the fragile 23-arg assembly is exposed to the client. This is the single most valuable change for this file.
- `behavior_validate` / `android_apk_signature` nullability encodes a known verification gap (the iOS-impersonation decision) — documented, but brittle if HelloTalk starts requiring the Android signature.

## Duplicate logic
- Shares the device-fingerprint field family (`device_id`, `client_version`, `os_type`, `client_lang`, etc.) conceptually with `EmailPreLoginRequest`, `SignCheckRequest`, and `RegPrepareRequest` — each upstream DTO redeclares the subset it needs. This is inherent to matching distinct upstream endpoints and is not harmful duplication, though a shared "device persona" value object could feed several of them.

## Dead or unused code
- None. Constructed in `HelloTalkAuthClientImpl.performLogin`; all accessors are Jackson-serialized onto the wire.

## Refactoring recommendations
- Add a `static EmailLoginRequest forEmailLogin(...)` factory (or builder) that takes the small set of truly-varying inputs and fills the persona constants internally — eliminating the 23-argument positional call in the client.
- Consider deriving the persona fields from a shared `DevicePersona` config object also used by the other upstream DTOs.
