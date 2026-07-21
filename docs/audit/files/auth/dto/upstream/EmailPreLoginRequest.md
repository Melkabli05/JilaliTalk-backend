# EmailPreLoginRequest

`src/main/java/com/jilali/auth/dto/upstream/EmailPreLoginRequest.java`

## Purpose
The wire body for `POST /user_register_center/v3/pre_login` (bin/cc2018) — step one of the two-step login exchange, which fetches the per-request `cnonce`/`nonce` salts needed to hash the password. Field shapes verified against smali `Lsd/c;`.

## Responsibilities
- Represent the pre_login request with exact wire names.
- Provide a `of(...)` factory that fills in the login-type/os-type constants for the email/Android case.

## Public API
Record components (wire names in parentheses):
- `int loginType (login_type)`, `String email`, `int osType (os_type)`, `String deviceId (device_id)`, `String clientVersion (client_version)`, `@Nullable String emailVerifyCode (email_verify_code)`.
- `static EmailPreLoginRequest of(String email, String deviceId, String clientVersion)` — builds with `login_type=1` (email), `os_type=1` (Android), null `email_verify_code`.
- Private constants `EMAIL_LOGIN_TYPE=1`, `ANDROID_OS_TYPE=1`.

## Dependencies
- `@JsonProperty`, `@Nullable`, `@Serdeable`.
- Depended on BY: `HelloTalkAuthClientImpl.preLogin` (via `of(...)`). Grep-confirmed: `HelloTalkAuthClientImpl` only.

## Coupling and cohesion analysis
High cohesion — one wire contract with a named constructor that encapsulates the constant fields. Low coupling. This is the well-shaped version of an upstream DTO: the `of(...)` factory hides the magic constants and exposes only the varying inputs — exactly what `EmailLoginRequest` lacks.

## Code smells
- **Data Class:** appropriate.
- **Primitive Obsession (minor):** int-encoded enums (`login_type`, `os_type`) mirror the wire; unavoidable.

## Technical debt
- None of note. The `of` factory hardcodes Android (`os_type=1`) while login itself impersonates iOS — consistent with the documented approach that only `/v3/login` uses the iOS persona, but the split of personas across steps is a subtle coupling worth remembering.

## Duplicate logic
- Shares device-fingerprint fields with the other upstream request DTOs (see `EmailLoginRequest.md`) — inherent, not harmful.

## Dead or unused code
- None. `of(...)` is called in `preLogin`; accessors serialized onto the wire. Private constants are used by `of`.

## Refactoring recommendations
- None required — this is a model for how `EmailLoginRequest` should be refactored (factory hides the constants).
