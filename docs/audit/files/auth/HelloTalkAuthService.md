# HelloTalkAuthService

`src/main/java/com/jilali/auth/HelloTalkAuthService.java`

## Purpose
The application service orchestrating the auth business flows (login, logout, current-user, and the four-step signup pipeline). It depends only on ports (`HelloTalkAuthClient`, `AuthSessionRepository`) plus `JilaliProperties` and — as documented inherited coupling from an in-progress DDD migration — the concrete `JilaliGateway` for profile enrichment.

## Responsibilities
- `login`: verify upstream credentials, then create a local session and build the enriched `AuthUserResponse`.
- `logout`: delete the session.
- `currentUser`: resolve a session id to an enriched user.
- Signup pipeline pass-throughs: `signupPrepare`, `signupSendEmailCode`, `signupCheckNickname`.
- `signup`: run the terminal `/v3/check` step, then immediately fall back into `login` (since `/v3/check` returns no JWT).
- `buildAuthUser`: best-effort profile enrichment via `JilaliGateway.userInfo`, degrading to a nameless identity on failure.

## Public API
- `HelloTalkAuthService(HelloTalkAuthClient, AuthSessionRepository, JilaliProperties, JilaliGateway)` — constructor injection.
- `LoginOutcome login(String email, String password)` — Authenticated (with new session) or InvalidCredentials.
- `void logout(String sessionId)`.
- `Optional<AuthUserResponse> currentUser(String sessionId)`.
- `void signupPrepare()`, `void signupSendEmailCode(String)`, `void signupCheckNickname(String)`.
- `SignupOutcome signup(String email, String password, String emailVerifyCode)` — Created or Rejected.
- Private `AuthUserResponse buildAuthUser(AuthSession)`.

## Dependencies
- Ports: `HelloTalkAuthClient`, `AuthSessionRepository`.
- `JilaliProperties` (deviceId, deviceModel), `JilaliGateway` (`userInfo`), `UserInfo` DTO.
- Outcomes: `LoginOutcome`, `SignupOutcome`; DTO `AuthUserResponse`.
- Depended on BY: `AuthController` only.

## Coupling and cohesion analysis
High cohesion — this is the auth use-case layer and it reads cleanly. Good DIP: HTTP/JDBC/crypto never leak in. The one wart is the direct `JilaliGateway` dependency (a concrete class from the `client` package spanning many bounded contexts), which the class Javadoc explicitly flags as inherited coupling awaiting a `user` context split. That is documented, intentional, and temporary rather than accidental.

## Code smells
- **Inappropriate Intimacy / Feature Envy (minor)** in `buildAuthUser` (lines 100–109): it reaches through `profile.details().base().headUrl()` — a three-level Law-of-Demeter chain into `UserInfo`'s nested structure. If any intermediate is null it is guarded, but the deep navigation belongs closer to `UserInfo`.
- **Temporal coupling / hidden second round-trip** in `signup` (lines 81–92): a successful `/v3/check` silently triggers a full second login. Correct per the upstream protocol, but a surprising side effect worth its documentation.

## Technical debt
- Direct `JilaliGateway` coupling pending the DDD `user`-context extraction (referenced spec: `docs/superpowers/specs/2026-07-09-ddd-migration-design.md` phase 3).
- The "account created but follow-up login failed" branch (lines 89–91) returns a Rejected with a user-facing string telling the user to log in manually — a real edge case with no retry.
- No tests.

## Duplicate logic
- None internal. Note the profile-navigation could overlap conceptually with `ProfileController`/`user` package logic (out of scope here).

## Dead or unused code
- None. All public methods are called by `AuthController`.

## Refactoring recommendations
- Introduce a narrow `UserProfilePort` (e.g. `nickname`/`headUrl` by uid) to replace the direct `JilaliGateway` dependency once the DDD migration lands — this is already the documented plan.
- Push the `details().base().headUrl()` navigation into a helper/accessor on `UserInfo` to remove the Demeter chain from the service.
- Consider distinguishing signup-rejection reasons (see `HelloTalkAuthClient` recommendation) so the 422 body is more actionable.
