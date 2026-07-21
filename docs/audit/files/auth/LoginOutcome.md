# LoginOutcome

`src/main/java/com/jilali/auth/LoginOutcome.java`

## Purpose
A sealed result type for `HelloTalkAuthService.login`. It models "wrong email/password" as an ordinary, data-level branch rather than as an exception, reserving `JilaliException` for genuinely exceptional transport/network failures. The sealed hierarchy lets `AuthController`'s `switch` be exhaustive at compile time.

## Responsibilities
- Enumerate the two possible outcomes of a login attempt: success or invalid credentials.
- Carry, in the success case, both the raw `AuthSession` (so the controller can read `session.id()` for the cookie) and the already-profile-enriched `AuthUserResponse` (the response body).

## Public API
- `sealed interface LoginOutcome` — permits exactly the two nested records.
- `record Authenticated(AuthSession session, AuthUserResponse user)` — success; both components non-null by construction.
- `record InvalidCredentials()` — failure; no data.

## Dependencies
- Imports `com.jilali.auth.dto.AuthUserResponse`; references `AuthSession` (same package).
- Depended on BY: `HelloTalkAuthService` (constructs both variants, return type of `login`) and `AuthController` (exhaustive `switch` maps variants to 200/401). Grep-confirmed: only these two callers.

## Coupling and cohesion analysis
High cohesion — a pure discriminated union with no behavior. Coupling is minimal and correct: it references only two domain types it must carry. This is the recommended "result object over exception-for-control-flow" pattern, and it is applied cleanly. It is a good example of using Java 17+ sealed types to make control flow total.

## Code smells
- None. `Authenticated` is a small data-carrying record, which is the intended shape of a sealed-union arm — not a Data Class smell in this context.

## Technical debt
- The failure arm is opaque: `InvalidCredentials()` collapses every distinct upstream refusal (wrong password, rate limit, risk block, banned account) into one value. This mirrors the same information loss already noted on `HelloTalkAuthClient.login` (which returns a bare `Optional.empty()`). If the frontend ever needs to distinguish these, a `reason` field or additional arms would be required.

## Duplicate logic
- Structurally parallel to `SignupOutcome` in the same package: both are two-arm sealed interfaces whose success arm is `(AuthSession session, AuthUserResponse user)`. `Authenticated` and `SignupOutcome.Created` are field-identical. This is mild, acceptable parallelism (see Refactoring), not harmful duplication.

## Dead or unused code
- None. Both records are constructed in `HelloTalkAuthService` and consumed in `AuthController`.

## Refactoring recommendations
- If richer error reporting is ever needed, add a `reason`/error-code to `InvalidCredentials` (or add discrete arms) and thread the upstream distinction down from `HelloTalkAuthClient`.
- The identical success arms of `LoginOutcome.Authenticated` and `SignupOutcome.Created` could share a small `AuthenticatedIdentity(session, user)` record if the parallelism grows, but at two arms each this is not yet worth the indirection.
