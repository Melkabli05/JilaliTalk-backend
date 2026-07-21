# EmailPreLoginResponse

`src/main/java/com/jilali/auth/dto/upstream/EmailPreLoginResponse.java`

## Purpose
The `data` payload of `POST /user_register_center/v3/pre_login`. It carries the per-request salts (`cnonce`/`nonce`) that `Md5Util.emailPasswordHash` needs, plus the resolved `user_id` used in the subsequent login.

## Responsibilities
- Model the three fields the login flow consumes from pre_login: `userId`, `cnonce`, `nonce`.

## Public API
Record components (wire names in parentheses):
- `long userId (user_id)`, `String cnonce`, `String nonce`. All Jackson-deserialized accessors.

## Dependencies
- `@JsonProperty`, `@Serdeable`.
- Depended on BY: `HelloTalkAuthClientImpl` (deserialized in `preLogin`, consumed by `performLogin`) and referenced by `HelloTalkEnvelope`'s Javadoc. Grep-confirmed: `HelloTalkAuthClientImpl` (runtime) and `HelloTalkEnvelope` (doc reference).

## Coupling and cohesion analysis
High cohesion — a focused response value object. It is deserialized from the `data` field of a `HelloTalkEnvelope<EmailPreLoginResponse>` (the envelope wrapping was itself the fix for a prior bug where the bare `data` shape was assumed to be the whole body). Low coupling. Well designed.

## Code smells
- **Data Class:** appropriate for a response DTO.

## Technical debt
- None of note. Fields are non-nullable here; if upstream ever omits `cnonce`/`nonce` on an error path, deserialization would yield nulls silently (Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false` and lenient missing-field handling) — but the envelope's `status`/`isSuccess()` gate is the intended guard against that.

## Duplicate logic
- None. Its `user_id` field overlaps conceptually with `LoginResponse.UserInfo.userId`, but they are distinct response stages.

## Dead or unused code
- None. Deserialized and consumed in the login flow.

## Refactoring recommendations
- None required. Optionally mark fields `@Nullable` and rely on `isSuccess()` for robustness on error responses.
