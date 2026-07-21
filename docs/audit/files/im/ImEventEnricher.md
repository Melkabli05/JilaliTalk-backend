# ImEventEnricher

`src/main/java/com/jilali/im/ImEventEnricher.java` — `@Singleton public class`.

## Purpose
Backfills missing identity fields (nickname, avatar) on IM realtime events whose raw upstream payload omits them — chiefly `ProfileVisit`, `Follow`, `GiftMessage`, `IntroductionMessage` — by looking up `JilaliGateway.userInfo(uid)` (Caffeine-cached). Reactive, never-erroring (falls back to the raw event).

## Responsibilities
- Route each event by type to a per-type enrichment method (`enrich`).
- Only enrich when both nickname and headUrl are blank (`isFilled`).
- Perform the cache/upstream lookup off the IM I/O thread (`resolveAsync` on `boundedElastic`).
- Swallow lookup errors, emitting the un-enriched event.

## Public API
- `ImEventEnricher(JilaliGateway gateway)`.
- `Mono<ImRealtimeEvent> enrich(ImRealtimeEvent event)`.
- All else private: `enrichProfileVisit`, `enrichFollow`, `enrichGift`, `enrichIntroduction`, `resolveAsync`, and static `isFilled`, `firstFilled`, `headUrlOf`, `parseUid`.

## Dependencies
- Imports: `JilaliGateway`, `ImRealtimeEvent`, `UserInfo`, `UserInfoResponse`, Reactor `Mono`/`Schedulers`.
- Injected: `JilaliGateway`.
- Depended on by: `ImEventSource` (constructor-injected, called in the connector's event listener). Grep-verified.

## Coupling and cohesion analysis
Good single responsibility: "fill in identity fields." Cohesive — every method serves that one goal. Coupling to `JilaliGateway` (one dependency) and to the specific `ImRealtimeEvent` record shapes it reconstructs. The reconstruction (rebuilding a full record with two fields swapped) is verbose but localized. Overall clean; one of the better-factored classes in the batch.

## Code smells
- **Duplicated record-rebuild boilerplate**: `enrichGift`/`enrichIntroduction`/`enrichFollow`/`enrichProfileVisit` each re-list every record component to change only nickname+headUrl (records are immutable, so a full re-`new` is required). `IntroductionMessage` rebuild re-passes 11 args (lines 92-99). Inherent to immutable records without a wither, but repetitive.
- **Shotgun Surgery risk**: adding a field to any enriched record forces editing the matching `enrichX` re-construction — easy to miss.
- Minor: `firstFilled` returns `""` for null-from-user but the raw event's original blank is dropped — intentional per javadoc.

## Technical debt
- The set of enriched types is hard-coded in the `switch`; new identity-bearing events (e.g. `VoiceRoomShared`) won't be enriched unless a case is added. Silent gap rather than a bug.
- No metrics on enrichment hit/miss/fallback rate — the `onErrorResume` swallow is invisible except a warn log.

## Duplicate logic
No realtime counterpart — `RoomEventSource`/`HtNotifyMapper` do **not** enrich; the room mapper already carries full identity fields on the wire (`mapStageUser`, `mapComment` pull nickname/head_url directly). So enrichment is an IM-only concern (DM notify frames genuinely omit identity). Not duplicated; a real asymmetry justifying IM-specific code.

## Dead or unused code
None. `enrich` is the single public entry, all private methods reachable. Grep-verified sole caller `ImEventSource`.

## Java 25 modernization opportunities
- The `switch` on the sealed `ImRealtimeEvent` (lines 40-46) is already idiomatic pattern-matching — good.
- A **record "wither"** pattern (or a shared `withIdentity(nickname, headUrl)` default method on an `Identifiable` sub-interface of `ImRealtimeEvent`) would remove the four verbose re-constructions. Java 25 has no built-in record wither, but a sealed sub-interface with an abstract `withIdentity` method makes the switch exhaustive and the rebuild local to each record.
- Nothing needs virtual threads here — Reactor + boundedElastic already handles the blocking hop.

## Micronaut built-in opportunities
- The caching this relies on lives in `JilaliGateway` (`@Cacheable("user-info")`) — already Micronaut-native; no change.
- Could be expressed with Micronaut's own reactive types, but Reactor `Mono` is fine and consistent with the rest of the pipeline.

## Refactoring recommendations
1. Introduce an `Identifiable` marker sub-interface (`ProfileVisit`/`Follow`/`GiftMessage`/`IntroductionMessage`) with `String nickname()`, `String headUrl()`, and `Identifiable withIdentity(String,String)`; collapse the four `enrichX` into one generic method.
2. Add enrichment counters (hit/miss/fallback) for observability.
3. Document the intentional non-enrichment of room-share events, or extend coverage.
