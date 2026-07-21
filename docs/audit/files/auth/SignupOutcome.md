# SignupOutcome

`src/main/java/com/jilali/auth/SignupOutcome.java`

## Purpose
A sealed result type for `HelloTalkAuthService.signup`. Like `LoginOutcome`, it turns expected upstream refusals into a data-level branch rather than an exception, keeping `AuthController`'s `switch` exhaustive.

## Responsibilities
- Enumerate the two outcomes of the terminal signup step: created or rejected.
- Carry, on success, the new `AuthSession` and the enriched `AuthUserResponse`.
- Carry, on rejection, a human-readable `reason` string (email taken, bad verification code, or the anti-cheat / device-attestation ceiling this BFF cannot clear).

## Public API
- `sealed interface SignupOutcome` — permits exactly the two nested records.
- `record Created(AuthSession session, AuthUserResponse user)` — success; both components non-null.
- `record Rejected(String reason)` — failure; `reason` is surfaced to the client as the 422 body.

## Dependencies
- Imports `com.jilali.auth.dto.AuthUserResponse`; references `AuthSession` (same package).
- Depended on BY: `HelloTalkAuthService` (constructs both variants) and `AuthController` (maps `Created`→201+cookie, `Rejected`→422+reason). Grep-confirmed: only these two.

## Coupling and cohesion analysis
High cohesion — a pure discriminated union. Coupling is minimal and correct. Same well-applied sealed-result pattern as `LoginOutcome`.

## Code smells
- **Primitive Obsession (mild):** `Rejected` carries a bare `String reason` rather than a typed error code or enum. It is a free-form string produced in the service and rendered verbatim by the controller, so the failure taxonomy is untyped and unenumerable. Distinct rejection causes (email taken vs bad code vs anti-cheat ceiling vs "account created but follow-up login failed") are indistinguishable to the frontend except by string matching.

## Technical debt
- The untyped `reason` string is the same information-loss theme seen across the package (`LoginOutcome.InvalidCredentials`, `HelloTalkAuthClient` `Optional.empty()`). One notable case — "account created but the automatic follow-up login failed" (see `HelloTalkAuthService.signup`) — is folded into the same `Rejected` arm as ordinary validation failures, even though it represents a partially-succeeded operation with different remediation (retry login vs fix input).

## Duplicate logic
- `Created(AuthSession, AuthUserResponse)` is field-identical to `LoginOutcome.Authenticated`. Mild, acceptable parallelism (see `LoginOutcome.md`).

## Dead or unused code
- None. Both records are constructed in `HelloTalkAuthService` and consumed in `AuthController`.

## Refactoring recommendations
- Replace the free-form `Rejected(String)` with a typed reason (enum or a small set of arms), so the frontend can react per-cause and the "partial success / retry login" case is distinguishable from a validation failure.
- Consider a shared success-arm record with `LoginOutcome` if the parallelism grows.
