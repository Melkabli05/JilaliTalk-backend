# Migration Roadmap

Expands the goal's 6-phase migration strategy into concrete, independently-shippable steps. Each step within a phase should land as its own commit, buildable (`./gradlew compileJava -x test` and `./gradlew test` both green) before the next step starts — the same discipline the prior refactor pass on the legacy codebase already established and that held up across 10 real commits.

## Phase 1 — Design the new architecture ✅ (this document set)

Deliverables: `00` through `06`, `08`, `09`, `10` in this directory. No source code. **Complete as of this commit.**

## Phase 2 — Implement the new Room module (domain + application layers only, no wiring to real traffic) ✅

Build `com.jilali.roomcontext.domain.*` and `com.jilali.roomcontext.application.*` completely, with `infrastructure` adapters backed by **in-memory fakes**, not real HTTP calls. This is deliberately sequenced before any real upstream integration so the domain model's correctness can be verified in isolation (unit tests only, no network, no Micronaut test context needed — see `05-port-definitions.md`'s testing note).

**Complete.** 5 aggregates (Room + Stage/RoomRoster/ManagerRoster child entities, RoomCommentThread, RoomSignIn, VipExperienceCard, UserProfile), value objects, the RoomEvent sealed hierarchy, a permissive policy seam, 7 port.in use-case interfaces, 7 application services, and 6 in-memory port.out adapters + a real Micronaut `ApplicationEventPublisher`-backed event publisher. Verified via full app boot (`./gradlew run`) with zero DI wiring errors alongside every legacy bean.

Concrete steps, each its own commit:
1. Value objects (`Cname`, `RoomUserId`, `HostId`, `ManagerId`, `BusiType`, `MicState`, `RoomLifecycleState`) + unit tests.
2. `Room` aggregate + `Stage`/`RoomRoster`/`ManagerRoster` child entities + unit tests covering every state transition in `01-domain-model.md`.
3. `RoomCommentThread`, `RoomSignIn`, `VipExperienceCard`, `UserProfile` aggregates + unit tests.
4. `RoomEvent` sealed hierarchy + a fake `RoomEventPublisherPort` for tests.
5. `port.in`/`port.out` interfaces (no implementations yet — just the contracts from `05-port-definitions.md`).
6. `application.service` classes implementing `port.in`, calling `domain.model` + `port.out`, tested against hand-written `port.out` fakes.

**Decision point to resolve during this phase, not deferred**: whether `StageController`'s legacy endpoints stay a separate `api.StageController` or merge into `api.RoomController`'s own `/room/{cname}/stage/*` sub-routes. The audit flagged `RoomController` itself as an over-large God Class — merging `Stage` endpoints INTO it would recreate that problem at the API layer even after fixing it at the domain layer. **Recommendation: keep `StageController` separate**, matching the legacy route structure (`/api/stage/*` is already its own path prefix), since `Room` and `Stage` being separate *entities in one aggregate* does not imply they need to be one *controller* — a controller's job is HTTP routing, and there is no HTTP-routing reason to merge two already-distinct path prefixes.

## Phase 3 — Migrate one use case at a time (wire to real upstream, one capability per commit) ✅

**Complete — all 8 capabilities, 83 of 83 endpoints, each verified live against the real HelloTalk upstream.**

**Revision note**: Phase 3 was first implemented as a strangler-fig pass (each capability's adapter delegating to the corresponding legacy `client.JilaliClient`/`client.JilaliGateway`/`client.ProfileClient`/`client.VipExperienceCardClient`/legacy services). That was superseded in a follow-up pass, per explicit feedback that depending on the legacy god client — even through an adapter — still left legacy code in the new bounded context's dependency graph. Every adapter now implements its port by calling a **dedicated `@Client` interface living inside `com.jilali.roomcontext.infrastructure.client`**, declared independently per capability (`TranslateUpstreamClient`, `VipJilaliClient`, `CommentJilaliClient`, `ManagerJilaliClient`, `SignInJilaliClient`, `StageJilaliClient`, `UserJilaliClient`, `UserProfileEncryptedClient`, `ProfileJilaliClient`, `RoomJilaliClient`), each calling HelloTalk directly. All ~90 wire DTOs were ported field-for-field into `infrastructure.dto.*` (same JSON shape, zero legacy import), and the three legacy fan-out services (`RoomJoinService`, `RoomsSearchService`, `ProfileBundleService`) were natively reimplemented against the new dedicated clients, preserving their exact `StructuredTaskScope`/retry logic.

A grep audit of every import under `com.jilali.roomcontext` confirms **zero remaining dependency on any legacy feature package** (`client`, `room`, `stage`, `comment`, `manager`, `signin`, `translate`, `user`, `vip`) — only genuinely shared platform code (`core.*`, `crypto.*`, `platform.models.RewardItem`, `platform.time.Seconds`) remains, plus one explicitly-documented exception: `realtime.RoomEventSource.audienceRevision()` for the live WebSocket relay's revision counter (a different subsystem — no HTTP call involved — not in scope for "replace the HTTP client").

1. ✅ **Translation** (1 endpoint) — dedicated `HtTranslateUpstreamClient` (imperative, SSE) + native `TranslationServiceImpl` (full Curve25519 handshake + AES field encryption + SSE decrypt, not a delegation).
2. ✅ **VIP** (5 endpoints) — dedicated `VipJilaliClient`; `claimTrial()`'s find-unused-24h-trial-then-activate logic reimplemented natively in `VipUpstreamAdapter`. Domain aggregate's state machine (`UNCLAIMED→CLAIMED→USED`) built in Phase 2 but not yet wired in front of the real call (needs a records-list-to-aggregate mapper — follow-up).
3. ✅ **Comment** (4 endpoints) — dedicated `CommentJilaliClient`.
4. ✅ **Manager** (4 endpoints) — dedicated `ManagerJilaliClient`.
5. ✅ **Sign-in** (7 endpoints) — dedicated `SignInJilaliClient`; the `room-level-bundle` fan-out lives in `application.service.SignInBundleService` (same `StructuredTaskScope` shape as the legacy inline version, now calling the dedicated client).
6. ✅ **User** (31 endpoints: `UserController` 13 + `ProfileController` 18) — dedicated `UserJilaliClient` + `UserProfileEncryptedClient` (imperative, Curve25519-encrypted userinfo + cc2018-encoded room-profile) for `UserController`; dedicated `ProfileJilaliClient` for `ProfileController`, preserving the visit/visitor-signing logic (`Md5Util.visitorHistorySign`) and follow/unfollow response normalization verbatim in `ProfileUpstreamAdapter`.
7. ✅ **Stage** (10 endpoints) — dedicated `StageJilaliClient`; ported `AgoraTokenCipher` (a self-contained crypto utility previously misplaced in the `room` feature package) into `infrastructure.crypto`.
8. ✅ **Room** (21 endpoints) — dedicated `RoomJilaliClient` for 16 pass-through calls; the join-bundle fan-out and search fan-out are natively reimplemented in `application.service.RoomJoinService`/`RoomsSearchService` against the dedicated clients (including `TextMatcher`, ported from `room.TextMatcher`), not delegated to the legacy services.

**Honest scope notes carried into Phase 4**: (a) VIP and Stage's real upstream integration doesn't yet route through the Phase-2 domain aggregates' state machines — the aggregates exist and are unit-testable in isolation, but wiring them in front of live calls needs an upstream-response-to-aggregate mapper not yet built; (b) two endpoint pairs flagged in `04-use-case-map.md` as likely-duplicate use cases (room `/config` vs signin `/room-level-config`; room `/join-bundle` vs user `/rooms/{cname}/join`) were each implemented once per legacy path, not consolidated — that consolidation is still open per `09-technical-risks.md` R4; (c) `RoomController.audienceReconcile`'s revision counter still depends on `realtime.RoomEventSource` (documented exception above).

## Phase 4 — Verify behavior against the legacy implementation

For each migrated capability: run both the legacy and new endpoint side-by-side (different path prefix during verification, e.g. `/api/v2/room/*` temporarily, or a feature-flag-gated router) against the same live HelloTalk session, diff the responses field-by-field. Given there is no local database (per `01-domain-model.md`'s "no persistence" note), this is a pure request/response comparison — no data-migration correctness to verify, only wire-contract correctness. This is meaningfully simpler than a typical "verify against legacy" phase in a system with real persisted state.

## Phase 5 — Switch consumers to the new implementation

Once Phase 4 passes for a capability, repoint the Angular frontend's API base path (or remove the temporary `/v2` prefix and retire the legacy route) for that capability only — capability by capability, not a single big-bang cutover. The Angular frontend project (`JilaliTalk-angular-frontend`, per this session's broader history) is unaffected as long as `10-compatibility-considerations.md`'s wire-contract guarantees hold; if they do, this phase requires zero frontend changes beyond the base path.

## Phase 6 — Remove legacy modules after complete feature parity

Delete `com.jilali.room`, `.stage`, `.comment`, `.manager`, `.signin`, `.translate`, `.user`, `.vip`, and the now-fully-emptied `com.jilali.client` god package. Rename `com.jilali.roomcontext` → `com.jilali.room` in one final mechanical commit (see `00-architecture-overview.md`'s package-root note). Delete the corresponding `docs/audit/files/{room,stage,comment,manager,signin,translate,user,vip}/**` and `docs/audit/packages/{room,stage,comment,manager,signin,translate,user,vip}*.md` — at that point they document code that no longer exists, and the `docs/room-redesign/*` set (renamed to become the new `docs/audit/` baseline for the renamed package) is the sole remaining source of truth.

## Sequencing rationale, restated

This mirrors exactly the sequencing lesson already learned once in this session (documented in `docs/audit/reports/roadmap.md`'s Phase 2 status note on the legacy `JilaliClient` split): small, independently-verifiable, low-concurrency-risk pieces first; the highest-fan-out, most-stateful piece (`Room`/`Stage`) last, once every pattern it depends on is proven elsewhere. The one time this session attempted a multi-file concurrent-state change without that kind of staged de-risking (the legacy `WebSocketConnectionLifecycle` full-migration attempt), it produced broken code and had to be reverted. This roadmap is built to not repeat that mistake at a much larger scale.
