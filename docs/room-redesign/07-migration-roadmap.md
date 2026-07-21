# Migration Roadmap

Expands the goal's 6-phase migration strategy into concrete, independently-shippable steps. Each step within a phase should land as its own commit, buildable (`./gradlew compileJava -x test` and `./gradlew test` both green) before the next step starts — the same discipline the prior refactor pass on the legacy codebase already established and that held up across 10 real commits.

## Phase 1 — Design the new architecture ✅ (this document set)

Deliverables: `00` through `06`, `08`, `09`, `10` in this directory. No source code. **Complete as of this commit.**

## Phase 2 — Implement the new Room module (domain + application layers only, no wiring to real traffic)

Build `com.jilali.roomcontext.domain.*` and `com.jilali.roomcontext.application.*` completely, with `infrastructure` adapters backed by **in-memory fakes**, not real HTTP calls. This is deliberately sequenced before any real upstream integration so the domain model's correctness can be verified in isolation (unit tests only, no network, no Micronaut test context needed — see `05-port-definitions.md`'s testing note).

Concrete steps, each its own commit:
1. Value objects (`Cname`, `RoomUserId`, `HostId`, `ManagerId`, `BusiType`, `MicState`, `RoomLifecycleState`) + unit tests.
2. `Room` aggregate + `Stage`/`RoomRoster`/`ManagerRoster` child entities + unit tests covering every state transition in `01-domain-model.md`.
3. `RoomCommentThread`, `RoomSignIn`, `VipExperienceCard`, `UserProfile` aggregates + unit tests.
4. `RoomEvent` sealed hierarchy + a fake `RoomEventPublisherPort` for tests.
5. `port.in`/`port.out` interfaces (no implementations yet — just the contracts from `05-port-definitions.md`).
6. `application.service` classes implementing `port.in`, calling `domain.model` + `port.out`, tested against hand-written `port.out` fakes.

**Decision point to resolve during this phase, not deferred**: whether `StageController`'s legacy endpoints stay a separate `api.StageController` or merge into `api.RoomController`'s own `/room/{cname}/stage/*` sub-routes. The audit flagged `RoomController` itself as an over-large God Class — merging `Stage` endpoints INTO it would recreate that problem at the API layer even after fixing it at the domain layer. **Recommendation: keep `StageController` separate**, matching the legacy route structure (`/api/stage/*` is already its own path prefix), since `Room` and `Stage` being separate *entities in one aggregate* does not imply they need to be one *controller* — a controller's job is HTTP routing, and there is no HTTP-routing reason to merge two already-distinct path prefixes.

## Phase 3 — Migrate one use case at a time (wire to real upstream, one capability per commit)

For each capability, in this order (least risky/most self-contained first, following the same sequencing logic that worked for the legacy refactor pass — small independent DTOs before anything touching concurrent WebSocket state):

1. **Translation** (1 endpoint, already has a correct port-and-adapter to move almost verbatim) — lowest risk, proves the plumbing end-to-end (`api` → `port.in` → `application.service` → `port.out` → `infrastructure.client` → real HelloTalk call) with minimal domain complexity.
2. **VIP** (5 endpoints, one aggregate with a real state machine, no fan-out concurrency) — proves the aggregate-with-state-transitions pattern.
3. **Sign-in** (7 endpoints, includes one `StructuredTaskScope` fan-out use case) — proves the fan-out pattern in the new structure, mirroring the legacy `SigninController.roomLevelBundle`.
4. **Comment** (4 endpoints) — proves the append-only aggregate pattern; also where the legacy `Comment`/`CommentDto` consolidation (already done in the legacy code) gets carried into the new domain model cleanly.
5. **Manager** (4 endpoints) — proves the command-per-action pattern without the complexity of a full `Stage`.
6. **User profile** (31 endpoints — the largest single migration, but almost entirely Queries with no complex state) — do this as several sub-commits (own-profile, other-user lookup, follow/unfollow, batch/presence), not one.
7. **Stage** (10 endpoints, the `Room` aggregate's most state-machine-heavy child entity) — deliberately late, once the surrounding patterns are proven on lower-risk capabilities.
8. **Room** itself (21 endpoints, the largest and most fan-out-heavy — `join-bundle` mirrors the legacy `RoomJoinService`'s multi-call `StructuredTaskScope` pattern) — deliberately last; by this point every port/pattern it depends on (Stage, Comment, User profile) already exists and is proven.

Each capability's migration includes: `infrastructure.client.<X>JilaliClient` (real `@Client` interface), `infrastructure.mapper.<X>Mapper` (wire DTO ↔ domain model), wiring the real adapter into the `port.out` bean (replacing the Phase 2 fake via Micronaut DI — no code change needed elsewhere, this is the whole point of the port), and the `api.<X>Controller` REST/WebSocket endpoints.

## Phase 4 — Verify behavior against the legacy implementation

For each migrated capability: run both the legacy and new endpoint side-by-side (different path prefix during verification, e.g. `/api/v2/room/*` temporarily, or a feature-flag-gated router) against the same live HelloTalk session, diff the responses field-by-field. Given there is no local database (per `01-domain-model.md`'s "no persistence" note), this is a pure request/response comparison — no data-migration correctness to verify, only wire-contract correctness. This is meaningfully simpler than a typical "verify against legacy" phase in a system with real persisted state.

## Phase 5 — Switch consumers to the new implementation

Once Phase 4 passes for a capability, repoint the Angular frontend's API base path (or remove the temporary `/v2` prefix and retire the legacy route) for that capability only — capability by capability, not a single big-bang cutover. The Angular frontend project (`JilaliTalk-angular-frontend`, per this session's broader history) is unaffected as long as `10-compatibility-considerations.md`'s wire-contract guarantees hold; if they do, this phase requires zero frontend changes beyond the base path.

## Phase 6 — Remove legacy modules after complete feature parity

Delete `com.jilali.room`, `.stage`, `.comment`, `.manager`, `.signin`, `.translate`, `.user`, `.vip`, and the now-fully-emptied `com.jilali.client` god package. Rename `com.jilali.roomcontext` → `com.jilali.room` in one final mechanical commit (see `00-architecture-overview.md`'s package-root note). Delete the corresponding `docs/audit/files/{room,stage,comment,manager,signin,translate,user,vip}/**` and `docs/audit/packages/{room,stage,comment,manager,signin,translate,user,vip}*.md` — at that point they document code that no longer exists, and the `docs/room-redesign/*` set (renamed to become the new `docs/audit/` baseline for the renamed package) is the sole remaining source of truth.

## Sequencing rationale, restated

This mirrors exactly the sequencing lesson already learned once in this session (documented in `docs/audit/reports/roadmap.md`'s Phase 2 status note on the legacy `JilaliClient` split): small, independently-verifiable, low-concurrency-risk pieces first; the highest-fan-out, most-stateful piece (`Room`/`Stage`) last, once every pattern it depends on is proven elsewhere. The one time this session attempted a multi-file concurrent-state change without that kind of staged de-risking (the legacy `WebSocketConnectionLifecycle` full-migration attempt), it produced broken code and had to be reverted. This roadmap is built to not repeat that mistake at a much larger scale.
