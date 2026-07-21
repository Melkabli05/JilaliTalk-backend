# Package `com.jilali.auth.dto.upstream`

## Purpose
The wire DTOs for HelloTalk's private `/user_register_center` auth microservice. Every type here mirrors, field-for-field and with exact `@JsonProperty` wire names, a request or response shape reverse-engineered from HelloTalk's smali bytecode. They are consumed exclusively by `HelloTalkAuthClientImpl` (plus `LoginResponse`, which is shared with the `im` package). They are intentionally kept separate from the BFF-facing `com.jilali.auth.dto` types.

## Responsibilities
- Represent the exact on-the-wire shapes for pre_login, login, reg/prepare, send_email_code, nickname check (reg/profile_check), and the terminal signup check (`/v3/check`).
- Model the generic response envelope (`HelloTalkEnvelope`) and the two response payloads (`EmailPreLoginResponse`, `LoginResponse`, `SignCheckResponse`).
- Encapsulate construction of the trickier requests via static factories that hide persona/protocol constants.

## Files in this package
| File | One-line summary |
|------|------------------|
| `EmailPreLoginRequest.java` | `POST /v3/pre_login` body; `of(...)` factory fills email/Android constants. |
| `EmailPreLoginResponse.java` | pre_login `data`: `user_id` + `cnonce`/`nonce` password salts. |
| `EmailLoginRequest.java` | `POST /v3/login` body; 23-field device persona + nested `account_login`. No factory (smell). |
| `LoginResponse.java` | login `data`: nested `user_info` carrying the all-important `jwt`. Shared with `im`. |
| `HelloTalkEnvelope.java` | Generic `{status,msg,data}` envelope; `isSuccess()`. |
| `RegPrepareRequest.java` | `POST /v3/reg/prepare` body; empty `irisk_token` (anti-cheat ceiling). |
| `SendEmailCodeUpstreamRequest.java` | `POST /v3/send_email_code` body; `forSignup(...)` fixes scene/behavior_validate. |
| `NicknameCheckUpstreamRequest.java` | `POST /v3/reg/profile_check` body: single `{nickname}`. |
| `SignCheckRequest.java` | `POST /v3/check` body; `forEmailSignup(...)` assembles `third_party_login` map. |
| `SignCheckResponse.java` | check `data`: only `verify_token` — no JWT (forces the login fallback). |

## Dependencies
- **Framework:** Jackson (`@JsonProperty`), Micronaut serde (`@Serdeable`), `@Nullable`, `java.util.Map`.
- **Internal:** none between these DTOs except `HelloTalkEnvelope` wrapping the response payloads at deserialize time; `EmailPreLoginResponse`'s salts feed `com.jilali.crypto.Md5Util`.
- **Consumed BY:** `HelloTalkAuthClientImpl` (all ten — request construction / response deserialization), `HelloTalkAuthClient` (interface signatures reference `LoginResponse`/`SignCheckResponse`), `HelloTalkAuthService` (`LoginResponse`/`SignCheckResponse`), and `com.jilali.im.HtImUpstreamConnector` (`LoginResponse`). No other consumers.

## Improvement opportunities
- **`EmailLoginRequest` needs a factory:** it is the only request DTO without one, forcing a fragile 23-argument positional construction in `HelloTalkAuthClientImpl.performLogin` (a Long-Method / error-prone hotspot). Add `forEmailLogin(...)` mirroring `SignCheckRequest.forEmailSignup` and `EmailPreLoginRequest.of`.
- **Shared device persona:** the device-fingerprint fields (`device_id`, `client_version`, `os_type`, `client_lang`, ...) are redeclared across `EmailLoginRequest`, `EmailPreLoginRequest`, `SignCheckRequest`, and `RegPrepareRequest`. A shared `DevicePersona` value object could feed all of them and remove the scattered magic constants in the client.
- **Unused response fields:** `LoginResponse.UserInfo`'s `areaCode`/`regTs`/`isAdult`/`isNewRegUser`/`isVip` have no read site (grep-verified) — only `userId`/`jwt` are consumed. Trim or document as intentionally-modeled.
- **Anti-cheat placeholders are brittle:** `RegPrepareRequest.iriskToken=""`, `SendEmailCodeUpstreamRequest.behaviorValidate=""`, and `EmailLoginRequest`'s nullable `behavior_validate`/`android_apk_signature` all encode documented verification gaps that could break or flag the account if upstream tightens validation.
- **Correctly not duplicated:** `HelloTalkEnvelope` (`{status,...}`) is deliberately distinct from `JilaliClient`'s `{code,msg,data}` envelope (different microservice/field) — a justified non-reuse, not a smell.
- These are all appropriate, well-documented wire DTOs; the smali-provenance Javadoc (especially on `EmailLoginRequest`, `SignCheckRequest`) is a model of reverse-engineering discipline and should be preserved through any refactor.
