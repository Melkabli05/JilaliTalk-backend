# SignCheckResponse

`src/main/java/com/jilali/auth/dto/upstream/SignCheckResponse.java`

## Purpose
The `data` payload of `POST /user_register_center/v3/check`. It models only `verify_token`; critically, this response carries **no JWT** (confirmed from smali `SignCheckResp`), which is precisely why `HelloTalkAuthService.signup` must fall back into the standard login pipeline to obtain a real token.

## Responsibilities
- Expose the single `verify_token` the signup-check step returns.

## Public API
- `record SignCheckResponse(@JsonProperty("verify_token") @Nullable String verifyToken)` — single nullable component; Jackson-deserialized accessor.

## Dependencies
- `@JsonProperty`, `@Nullable`, `@Serdeable`.
- Depended on BY (grep-confirmed): `HelloTalkAuthClient` (interface return type), `HelloTalkAuthClientImpl.signupCheck` (return + `verify_token` filtering), `HelloTalkAuthService.signup` (consumes the outcome).

## Coupling and cohesion analysis
Maximally cohesive (one field), low coupling. A minimal response DTO whose very thinness (no JWT) documents an important protocol fact.

## Code smells
- **Data Class:** appropriate for a response DTO.

## Technical debt
- The response also returns `user_info`/`area_code`/`banned_info` upstream, which are deliberately not modeled (the login fallback supplies identity instead). Documented and justified — not debt, but a note for any future caller that wants the banned/area info without a second login round-trip.

## Duplicate logic
- None.

## Dead or unused code
- None. Return type of `signupCheck`; `verifyToken()` is read by the client. Its `@Nullable` guard (empty when rejected) is used to distinguish success from refusal.

## Refactoring recommendations
- None required. If the "account created but follow-up login failed" edge case (see `SignupOutcome`/`HelloTalkAuthService`) needs richer handling, this response's `banned_info`/`user_info` could be modeled to avoid the second round-trip — but only if a concrete need arises.
