# HelloTalkEnvelope

`src/main/java/com/jilali/auth/dto/upstream/HelloTalkEnvelope.java`

## Purpose
The generic response envelope for the `/user_register_center` auth microservice: `{"status":0,"msg":"success","data":{...}}`. It wraps every cc2018 auth response so the client can check success and unwrap the typed `data`. Confirmed live against a real `pre_login` response — its introduction fixed a bug where the bare `data` shape was assumed to be the whole body (silently deserializing to null/zeroed fields).

## Responsibilities
- Model the `status`/`msg`/`data` envelope generically over the payload type `T`.
- Answer `isSuccess()` (status == 0).

## Public API
- `record HelloTalkEnvelope<T>(int status, @Nullable String msg, @Nullable T data)` — generic; `msg`/`data` nullable.
- `boolean isSuccess()` — true when `status == 0`.

## Dependencies
- `@Nullable`, `@Serdeable`.
- Depended on BY: `HelloTalkAuthClientImpl.readEnvelope` (constructs the parametric type and deserializes). Its Javadoc references `EmailPreLoginResponse`/`LoginResponse` as the `T` payloads and contrasts with `JilaliClient`'s `{code,msg,data}` envelope. Grep-confirmed: `HelloTalkAuthClientImpl` (runtime).

## Coupling and cohesion analysis
High cohesion — a generic envelope with one predicate. Low coupling. Its deliberate separateness from `JilaliClient`'s `{code,msg,data}` envelope (different field name, different microservice) is documented and correct — the two are genuinely distinct wire contracts, so *not* reusing one is the right call, not duplication.

## Code smells
- **Data Class (with one predicate):** the `isSuccess()` behavior lifts it just above a pure data bag. Appropriate.

## Technical debt
- The success test is hardcoded to `status == 0`; non-zero statuses carry no typed meaning here (the client collapses them to `Optional.empty()`), so distinct upstream error codes are discarded at this layer — the same information-loss theme as `LoginOutcome`/`HelloTalkAuthClient`.

## Duplicate logic
- Conceptually parallel to `JilaliClient`'s `{code,msg,data}` envelope elsewhere in the codebase, but intentionally not shared (different field names/microservice). This is documented and justified — a rare case where two similar-looking types are correctly kept separate.

## Dead or unused code
- None. `isSuccess()` and the accessors are used in `readEnvelope`.

## Refactoring recommendations
- If the frontend ever needs to distinguish upstream error causes, expose `status`/`msg` up the call chain instead of collapsing non-success to empty.
