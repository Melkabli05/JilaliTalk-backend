# ProfileBundleService

`src/main/java/com/jilali/user/ProfileBundleService.java` (143 lines)

## Purpose
Aggregator/orchestration service that assembles everything a profile page needs in one round trip, fanning the upstream calls out concurrently using JDK Structured Concurrency (`StructuredTaskScope`) on virtual threads. Backs `GET /api/profile/{userId}/bundle`.

## Responsibilities
- Decide self-vs-other dispatch: if the requested `userId` is the caller's own account, fetch own `stats` + `limitations`; otherwise fetch `payChatInfo` + `reminderMoment`.
- Run the essential `userInfo` fetch as a failure-propagating fork (its failure fails the whole bundle).
- Run the four supplementary fetches as error-isolated forks that degrade to `null` on their own upstream failure.
- Assemble the results into a `ProfileBundleResponse`.

## Public API
- `ProfileBundleService(JilaliGateway gateway, ProfileClient profileClient)` — constructor DI.
- `ProfileBundleResponse bundle(long userId)` — fans out and assembles the bundle; throws `RuntimeException` if `userInfo` itself fails or the thread is interrupted.
- Private helpers: `fetchOwnStatsOrNull()`, `fetchLimitationsOrNull()`, `fetchPayChatInfoOrNull(long)`, `fetchReminderMomentOrNull(long)` — each catches `RuntimeException`, logs a warn, and returns `null`.

## Dependencies
- Injects `JilaliGateway` (for `currentUserId()` and `userInfo(userId)`) and `ProfileClient` (for `stats`, `limitations`, `payChatInfo`, `reminderMoment`).
- Imports DTOs: `PayChatInfoResponse`, `ProfileBundleResponse`, `ProfileLimitationsResponse`, `ProfileStatsResponse`, `ReminderMomentResponse`.
- **Depended on by:** `ProfileController.bundle` (only caller). Referenced in `ProfileBundleResponse` javadoc and `JilaliGateway` javadoc.

## Coupling and cohesion analysis
High cohesion — every method serves the single "build the profile bundle" concern. Coupling is moderate: it reaches through two collaborators and depends on 5 nested response `*.Data` types. It duplicates the resiliency pattern of `RoomJoinService.joinBundle` (explicitly noted in javadoc), so the two share a conceptual coupling that is not factored into a shared helper.

## Code smells
- **Duplicated code (four near-identical `fetch*OrNull` helpers)**: lines 104-142. Each is the same try/catch/log/null-map skeleton differing only in the client call and the log message. This is the clearest smell.
- **Mild Primitive Obsession**: `boolean isOwnProfile` plus positional nulls into the 6-arg `ProfileBundleResponse` constructor (lines 87-94) — easy to transpose arguments.
- Long-ish method `bundle` (~50 lines) but justified by the structured-concurrency scope lifecycle.

## Technical debt
- The four `fetchXOrNull` methods should collapse into one generic `<T> T fetchOrNull(Supplier<Resp> call, Function<Resp,T> extract, String label)`.
- `callerUid == userId` (line 55) compares a boxed `Long` unboxed against a `long` — fine here because null is guarded, but the pattern is fragile.

## Duplicate logic
- The `resp != null ? resp.data() : null` unwrap appears 4× here and conceptually matches the `JilaliResponses.unwrap` usage in `UserController` and the raw client calls in `ProfileController` — a shared "safe unwrap" utility could serve all three.
- Structurally mirrors `RoomJoinService.joinBundle` (outside this batch) — a candidate for a shared `BundleScope` abstraction.

## Dead or unused code
None. `bundle` is invoked by the controller; the private helpers are all forked. Framework note: none of this is reflectively invoked — it is a plain `@Singleton` service.

## Refactoring recommendations
1. Extract the generic `fetchOrNull` helper to remove the 4-way duplication.
2. Replace the positional `ProfileBundleResponse` construction with a builder or two-branch factory (own vs other) to prevent argument transposition.
3. Consider extracting the structured-concurrency scaffolding shared with `RoomJoinService` into a reusable `ConcurrentBundle` utility.
