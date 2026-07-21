# SendEmailCodeUpstreamRequest

`src/main/java/com/jilali/auth/dto/upstream/SendEmailCodeUpstreamRequest.java`

## Purpose
The wire body for `POST /user_register_center/v3/send_email_code` (ht/encbin), matching smali `Ls21/f;`: `{behavior_validate, email, scene}`. The `scene` value is hardcoded upstream (`s21/f;`'s only constructor), so `new_device_login` is the value for every caller despite the "signup" naming.

## Responsibilities
- Represent the send-email-code request on the wire.
- Provide `forSignup(email)`, which fixes `behavior_validate=""` and `scene="new_device_login"`.

## Public API
Record components (wire names in parentheses):
- `String behaviorValidate (behavior_validate)`, `String email`, `String scene`.
- `static final String NEW_DEVICE_LOGIN_SCENE = "new_device_login"`.
- `static SendEmailCodeUpstreamRequest forSignup(String email)` — builds with empty `behavior_validate` and the fixed scene.

## Dependencies
- `@JsonProperty`, `@Serdeable`.
- Depended on BY: `HelloTalkAuthClientImpl.sendEmailCode` (via `forSignup`). Grep-confirmed: `HelloTalkAuthClientImpl` only.

## Coupling and cohesion analysis
High cohesion, low coupling. A well-shaped upstream DTO whose `forSignup(...)` factory encapsulates the constant fields (like `EmailPreLoginRequest.of` and `SignCheckRequest.forEmailSignup`, unlike `EmailLoginRequest`).

## Code smells
- **Data Class:** appropriate.
- `behavior_validate` sent empty encodes the same anti-cheat gap as elsewhere — the upstream currently treats it as a presence check; brittle if that changes.

## Technical debt
- Empty `behavior_validate` is a deliberate anti-fraud bypass tied to the placeholder used across the client; documented but could break or flag the account if upstream tightens validation.

## Duplicate logic
- Shares `behavior_validate` / `email` fields with other upstream requests — inherent. Unlike `NicknameCheckRequest`, the BFF-facing `SendEmailCodeRequest` (single `email`) is genuinely thinner than this three-field wire type, so real transformation occurs (no needless-mapping smell).

## Dead or unused code
- None. `forSignup`, `NEW_DEVICE_LOGIN_SCENE`, and accessors are all used/serialized.

## Refactoring recommendations
- None required. Centralize the `behavior_validate` placeholder policy with the client's other anti-cheat constants if that value is ever externalized to config.
