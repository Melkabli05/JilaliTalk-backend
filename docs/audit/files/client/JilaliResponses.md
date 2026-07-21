# JilaliResponses.java

`src/main/java/com/jilali/client/JilaliResponses.java`

## Purpose
Single shared utility that turns a `JilaliEnvelope<T>` into either its payload or a typed `JilaliException`. The one canonical envelope-unwrapping seam for the whole codebase.

## Responsibilities
- `unwrap`: null-check envelope → `BAD_GATEWAY`; if `!isSuccess()` throw `JilaliException.fromCode(code, msg)`; else return `data()` (may be null).
- `requireData`: `unwrap` + throw if data is null (for endpoints where null is never valid success).

## Public API
`public final class JilaliResponses` — private constructor (non-instantiable):
- `public static <T> T unwrap(JilaliEnvelope<T> envelope)`
- `public static <T> T requireData(JilaliEnvelope<T> envelope)`

## Dependencies
- Uses `JilaliEnvelope`, `JilaliException`, `HttpStatus`.
- Depended on by (grep): `CommentController`, `SigninController`, `ManagerController`, `RoomController`, `RoomsSearchService`, `RoomJoinService`, `UserController`, `ProfileBundleService`, `VipExperienceCardController`, `JilaliGateway`. Very widely used — the standard unwrap path.

## Coupling and cohesion analysis
High cohesion (single pure transform), minimal coupling (three core types, stateless). Exemplary utility. The Javadoc itself justifies why this is package-private-scoped and static rather than a `@Singleton`.

## Code smells
None. This is a well-scoped, intentional stateless utility. The class-level comment explicitly guards against it becoming a dumping ground.

## Technical debt
None. Note however the *inconsistency in adoption*: several call sites bypass it and unwrap envelopes ad hoc via `envelope.data()` directly — `RoomController:285`, `ProfileController:111-114`, `ProfileBundleService:107/117/127/137`, `HelloTalkAuthClientImpl:263`. Those are `ProfileClient`/`HelloTalkEnvelope` shapes (different envelope), so partly justified, but `RoomController:285` unwraps a `JilaliEnvelope` manually. The unwrap discipline is not 100% uniform (debt lives in the callers, not here).

## Duplicate logic
None here. It *is* the deduplication point. The residual ad-hoc `.data()` accesses noted above are the anti-pattern this class exists to prevent.

## Dead or unused code
None. Both methods used (`requireData` used by `SigninController.roomLevelConfig`/`roomLevelBundle`).

## Java 25 modernization opportunities
Minimal — already a tight generic static API. Could not meaningfully benefit from records/pattern matching. Leave as is.

## Micronaut built-in opportunities
The strongest opportunity in the whole client layer: this manual unwrap-at-every-call-site could be replaced by a Micronaut **`@ClientFilter`/response filter** (or a custom `HttpClientResponseExceptionHandler`) that unwraps `JilaliEnvelope` and maps non-zero codes to `JilaliException` centrally, so `JilaliClient` methods could return `T` directly instead of `JilaliEnvelope<T>`. That would eliminate ~80 `JilaliResponses.unwrap(...)` call sites. Trade-off: loses the ability to inspect the raw envelope (a few callers want it) — hence `requireData`/`unwrap` split. A hybrid (filter for the common case, raw client for the rare case) is viable.

## Refactoring recommendations
1. Keep the class; enforce its use — replace the remaining ad-hoc `envelope.data()` accesses (esp. `RoomController:285`) with `unwrap`/`requireData`.
2. Longer term, evaluate migrating envelope unwrapping into a Micronaut response filter (see above) to shrink the boilerplate surface for the rewrite.
