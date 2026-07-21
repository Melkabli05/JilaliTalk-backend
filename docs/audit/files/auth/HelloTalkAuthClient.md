# HelloTalkAuthClient

`src/main/java/com/jilali/auth/HelloTalkAuthClient.java`

## Purpose
The port (interface) to HelloTalk's own auth microservice (`/user_register_center/**`). It is deliberately narrow (Interface Segregation) and business-oriented: each method is a complete auth operation, not a raw wire call. Notably `login` hides the two-step pre_login+login exchange from callers.

## Responsibilities
- Define the upstream auth contract: full login exchange, reg/prepare, send email code, nickname check, and the terminal signup check.

## Public API
- `Optional<LoginResponse> login(String email, String password)` — runs pre_login + login; empty on any credential rejection, throws `JilaliException` on transport/decode failure.
- `void regPrepare()` — best-effort anti-cheat token bind; never throws.
- `void sendEmailCode(String email)` — triggers HelloTalk to email a verification code.
- `void checkNickname(String nickname)` — nickname availability/validity check.
- `Optional<SignCheckResponse> signupCheck(String email, String password, String emailVerifyCode)` — terminal signup step; empty on rejection; never returns a JWT.

## Dependencies
- DTOs: `LoginResponse`, `SignCheckResponse`; `java.util.Optional`.
- Implemented BY: `HelloTalkAuthClientImpl`.
- Depended on BY: `HelloTalkAuthService`, and (outside this package) `im/ImEventSource` and `im/HtImUpstreamConnector`, which reuse `login` to mint JWTs for the IM socket.

## Coupling and cohesion analysis
High cohesion, correct abstraction seam. The interface documents important behavioral contracts (empty vs throw, "never returns a JWT"). Good use of ISP — it is intentionally NOT folded into the shared `JilaliClient`. The fact that it is consumed from the `im` package as well shows it is a genuinely shared port, not auth-private.

## Code smells
- None at the interface level. `signupCheck`'s three String params are a mild Long Parameter List but acceptable.

## Technical debt
- The contract "any rejection is conservatively treated as invalid credentials" (documented on `login`) means real distinct upstream errors (rate limit, banned, risk-block) are all collapsed to `Optional.empty()`. This loses information the frontend might want to surface.

## Duplicate logic
- None (interface only).

## Dead or unused code
- None. All five methods are called from `HelloTalkAuthService`; `login` additionally from the `im` package.

## Refactoring recommendations
- Consider a richer return type for `login`/`signupCheck` (a small result enum/sealed type) so callers can distinguish "wrong password" from "rate limited"/"risk blocked" instead of a bare `Optional.empty()`.
