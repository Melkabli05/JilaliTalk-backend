# Package `com.jilali.auth.dto`

## Purpose
The BFF-facing DTOs for the auth HTTP boundary — the request bodies the Angular frontend sends to `/api/auth/**` and the response shapes it receives. These types are dictated by the frontend contract (built ahead of this backend) and are kept deliberately separate from the upstream HelloTalk wire types in `com.jilali.auth.dto.upstream`.

## Responsibilities
- Define validated inbound request bodies for login, signup-check, send-email-code, and nickname-check.
- Define the outbound user identity (`AuthUserResponse`) and its `{user: ...}` envelope (`AuthResponse`) matching the frontend's `AuthUser`/`AuthResponse` types.

## Files in this package
| File | One-line summary |
|------|------------------|
| `AuthUserResponse.java` | Outbound user identity (userId/nickname/email/headUrl + IM credentials); two static factories. |
| `AuthResponse.java` | `{ user: AuthUserResponse }` envelope for `GET /api/auth/me`. |
| `LoginRequest.java` | `POST /login` body: `@Email email` + `@NotBlank password`. |
| `SignupCheckRequest.java` | `POST /signup/check` body: email + password + emailVerifyCode. |
| `SendEmailCodeRequest.java` | `POST /signup/send-email-code` body: `@Email email`. |
| `NicknameCheckRequest.java` | `POST /signup/check-nickname` body: `@NotBlank nickname`. |

## Dependencies
- **Framework:** Micronaut serde (`@Serdeable`), `jakarta.validation` (`@Email`, `@NotBlank`), `@Nullable`.
- **Internal:** `AuthUserResponse` depends on `com.jilali.auth.AuthSession` (its factories map domain -> DTO).
- **Consumed BY:** `AuthController` (all six — deserializes requests, renders responses) and `HelloTalkAuthService`/`LoginOutcome`/`SignupOutcome` (which carry `AuthUserResponse`). No consumers outside the `auth` package.

## Improvement opportunities
- **Envelope consistency:** `AuthResponse` (`{user: ...}`) is used only by `me`; `login`/`completeSignup` rebuild the same shape inline in `AuthController`. Route all three through one wrapper/factory.
- **`imJwt` exposure (security):** `AuthUserResponse` deliberately ships the real HelloTalk JWT (`imJwt`) to the browser for its own IM socket — the one place the "JWT never leaves the backend" invariant is broken. Revisit whether the socket can be proxied server-side.
- **Needless-mapping note:** `NicknameCheckRequest` is a single-`nickname` record that is near-identical to the upstream `NicknameCheckUpstreamRequest` (the only difference is the `@NotBlank` validation). The other request DTOs (`LoginRequest`, `SignupCheckRequest`, `SendEmailCodeRequest`) do map to genuinely thicker/transformed upstream types, so this is the only weak instance of the smell here.
- **Validation depth:** no max-length bounds on any field; add for defense-in-depth. Consider rate-limiting the send-email-code path (spammable) upstream of `SendEmailCodeRequest`.
- All six are appropriate Data Classes — no structural refactor needed; the notes above are contract/security polish.
