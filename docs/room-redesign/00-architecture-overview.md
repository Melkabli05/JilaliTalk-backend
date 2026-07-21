# Room Bounded Context — Architecture Overview

> Status: design phase (Phase 1 of the migration strategy). No implementation code exists yet under the new package. The legacy `com.jilali.room`, `com.jilali.stage`, `com.jilali.comment`, `com.jilali.manager`, `com.jilali.signin`, `com.jilali.translate`, `com.jilali.user`, `com.jilali.vip` packages remain the running system and the source of truth for behavior until the new implementation reaches parity.

## Why one bounded context, not eight packages

The completed audit (`docs/audit/packages/{room,stage,comment,manager,signin,translate,user,vip}.md`) shows these eight legacy packages are not independent business capabilities — they are different facets of one thing: **a live HelloTalk voice room and the person interacting with it**. Concretely:

- You cannot join a **Stage** seat without being a member of a **Room**.
- You cannot post a **Comment** without being in a **Room**.
- **Manager** grants only make sense scoped to a **Room** (`cname` + `host_id`).
- **Sign-in** rewards are claimed per `(host_id, cname)` — a room-level engagement mechanic, not a standalone feature.
- **VIP** card claim/use/display surfaces inside a room's UI (badge display, feature-right checks scoped to the room session), even though the card's own lifecycle outlives any one room visit.
- **User** profile lookups exist almost entirely to answer "who is this person I'm seeing in the room roster."
- **Translation** exists to translate what's said/written inside a room.

None of the eight has a reason to exist without the others. That is the textbook definition of one bounded context with several capabilities, not eight bounded contexts.

## Package root during migration

The legacy code owns `com.jilali.room`, `com.jilali.stage`, etc. To satisfy "the existing implementation remains untouched during migration," the new implementation is built under a **new, temporary root package**: `com.jilali.roomcontext`. When Phase 6 (legacy removal) completes, `com.jilali.roomcontext` is renamed to `com.jilali.room` in one final mechanical commit — at that point there is no more ambiguity to avoid.

```
com.jilali.roomcontext          <- NEW, this design
com.jilali.room, .stage, ...    <- LEGACY, untouched until Phase 6
```

## Layering (lightweight Hexagonal)

```
                          ┌─────────────────────────────┐
                          │   api/ (inbound adapters)    │
                          │  REST controllers, WS        │
                          │  controllers, request/       │
                          │  response DTOs, mappers      │
                          └──────────────┬───────────────┘
                                         │ calls
                          ┌──────────────▼───────────────┐
                          │   application/ (use cases)    │
                          │  commands, queries, services,  │
                          │  port.in (use-case interfaces),│
                          │  port.out (what the app needs) │
                          └──────────────┬───────────────┘
                                         │ calls domain, calls port.out
                          ┌──────────────▼───────────────┐
                          │   domain/ (business rules)     │
                          │  Room aggregate + siblings,     │
                          │  value objects, policies,       │
                          │  domain events, domain services │
                          └───────────────────────────────┘
                                         ▲
                                         │ implements port.out
                          ┌──────────────┴───────────────┐
                          │  infrastructure/ (adapters)    │
                          │  HTTP client to HelloTalk,     │
                          │  WebSocket relay, cache,       │
                          │  config, mappers to wire DTOs  │
                          └───────────────────────────────┘
```

Dependency direction is strictly inward: `api` → `application` → `domain`. `infrastructure` also points inward (it implements `domain`/`application` port interfaces) but nothing in `domain` or `application` imports `infrastructure`.

**This is lighter than textbook Hexagonal in one specific way, deliberately**: there is no local database and no "persistence" in the traditional sense. HelloTalk's own backend is the system of record for every entity in this context. The `domain.repository` ports are **upstream-facing** ports (read/write to HelloTalk over HTTP), not database repositories. `infrastructure.persistence` in the target package tree is therefore mostly empty/absent for this context — see `05-port-definitions.md` for the actual port list, and `09-technical-risks.md` for why this matters (there is no local transaction boundary to reason about; HelloTalk's upstream API is the only source of truth and the only place invariants are truly enforced).

## CQRS — where it's actually used, and where it's deliberately NOT

Applied only where the audit shows a genuine read/write asymmetry with different consumers and different failure/caching semantics:

| Area | CQRS? | Why |
|---|---|---|
| Room listing/search/browse (`GET /voice`, `/live`, `/{type}/search`, category/topic browsing) | **Yes — Query** | Pure reads, heavily cacheable, no business-rule enforcement, different scaling profile than writes (search is called far more often than create/join). |
| Room join / create / end | **Yes — Command** | Writes with real invariants (only a host can end their own room; join has a parallel-fanout read-after-write shape already proven in `RoomJoinService`). |
| Stage actions (raise-hand, kick, invite, device-control) | **Yes — Command**, one per action | Each is a discrete state transition with its own authorization/validation rule (see `01-domain-model.md`'s `StageAction` polymorphism note — this replaces the audit's retracted "sealed interface" idea with per-command classes instead, see below). |
| Manager grant/revoke/approve | **Yes — Command** | Same reasoning as stage. |
| Comment posting | **Command** (write) + **Query** (history) | Simple split, not because of complex invariants but because posting and reading have entirely different consumers (posting: the commenter; reading: everyone in the room, paginated). |
| Sign-in claim | **Command** (claim) + **Query** (panel/tasks) | Same shape as comment. |
| VIP card claim/use | **Command** | Has real state transitions (a card can only be used once — see `01-domain-model.md`). |
| User profile lookup, follow/unfollow, batch status | **Mixed** — lookups are **Query**, follow/unfollow/block are **Command** | Follow/unfollow are genuine state mutations; the rest of `user` (batch status, presence) is pure read. |
| Translation | **Neither** | A single stateless request/response operation has no read/write asymmetry to exploit. Modeled as a plain domain service call, not a command or query. Forcing CQRS here would be exactly the kind of "CQRS everywhere" the goal explicitly says not to do. |

**On the retracted "sealed interface" idea**: the completed audit's `stage.md`/`manager.md` package docs proposed a `sealed StageAction`/`sealed ManagerAction` interface to consolidate the near-mirror `*Request` DTOs. During the prior refactor pass (see `docs/audit/reports/duplication.md`, items #7/#8, "retracted"), this was investigated and found unsound: each request is bound to a *different* wire endpoint with no discriminator field, so a sealed interface has no legitimate JSON deserialization target. **The new architecture resolves the same underlying complaint differently and correctly**: each action becomes its own `Command` record in `application.command.stage`/`application.command.manager` (`RaiseHandCommand`, `KickCommand`, `InviteToStageCommand`, …) — small, single-purpose, named for the business action they represent, with NO shared interface forced between them. This satisfies "encapsulate write operations as commands" without resurrecting the sealed-interface dead end.

## Java 25 usage

- **Records** for every Command, Query, Value Object, and immutable domain snapshot.
- **Sealed interfaces** where there is genuine closed polymorphism with a single dispatch point — e.g. `RoomEvent` (the domain event hierarchy) and the existing, already-correct `ImRealtimeEvent`/`RoomRealtimeEvent` from the legacy `im`/`realtime` packages, which this design treats as **infrastructure-layer wire events**, translated into `RoomEvent` domain events at the boundary (see `01-domain-model.md`).
- **Pattern matching + enhanced switch** for event translation and for the stage/manager command dispatch in the application layer (`switch (command) { case RaiseHandCommand c -> ...; case KickCommand c -> ...; }`).
- **Virtual threads** via `StructuredTaskScope` for every fan-out use case (room join, profile bundle, room-level bundle) — this pattern already exists correctly in the legacy `RoomJoinService`/`SigninController`/`ProfileBundleService` and is carried forward unchanged in spirit, just relocated to the application layer's use-case services.
- **Value objects as records with compact constructors** for primitive-obsession fixes: `Cname`, `HostId`, `RoomUserId` wrap the bare `String`/`long` values passed everywhere in the legacy code.

## Micronaut usage

- `@Client` declarative interfaces per capability (breaking up the legacy God interface — see `06-package-dependency-analysis.md`), each still pointed at the same `id="jlhub"` HTTP client configuration, so there is exactly one underlying connection pool/config, matching the legacy `JilaliClient`'s own documented one-upstream rationale.
- `@Cacheable` for read-heavy queries (room search, profile bundle, VIP feature-right) — already proven in `ProfileBundleService`/`TranslateService`.
- `@Retryable` for the one place it earned its keep in the legacy pass (IM relogin) — carried forward as a pattern for any new transient-failure-prone upstream call, not forced everywhere.
- `ApplicationEventPublisher`/`@EventListener` for `RoomEvent` propagation to the WebSocket relay layer, replacing the legacy `Sinks.Many` pub-sub pattern used in `ImEventSource`/`RoomEventSource` — this is new (not yet done in the legacy code either) and is one of the concrete "replace custom code with Micronaut built-ins" wins this redesign delivers.
- `@ConfigurationProperties` for any new config surface, consistent with the existing `JilaliProperties`.
- Virtual threads are Micronaut's default executor model in this codebase already (Micronaut 4.x + Java 25 `--enable-preview`); nothing new to configure.

## What does NOT change

Per the durable instruction carried over from the prior refactor goal: **security and authorization are out of scope.** The audit flagged CRITICAL authorization gaps in `manager` and `vip` (T-2/T-3) — these are **not** addressed by this redesign, by explicit user direction ("security and authorization are not real issue cuz the hellotalk real backend do these stuffs and not our proxy"). The new domain model does define a `Policy` extension point (see `01-domain-model.md`) purely so a future decision to add authorization has a clean seam — but its default implementation is a permissive no-op, preserving current (unchecked) behavior exactly.

## Document index

| # | Document | Purpose |
|---|---|---|
| 00 | `00-architecture-overview.md` | This document. |
| 01 | `01-domain-model.md` | Aggregates, entities, value objects, domain events, domain services, policies. |
| 02 | `02-aggregate-diagram.md` | Mermaid diagram of aggregate boundaries and references. |
| 03 | `03-dependency-diagram.md` | Mermaid diagram of layer/package dependencies (new structure). |
| 04 | `04-use-case-map.md` | Every legacy endpoint mapped to a new command/query. |
| 05 | `05-port-definitions.md` | `port.in` (use-case interfaces) and `port.out` (upstream/infra ports). |
| 06 | `06-package-dependency-analysis.md` | New package graph; how it resolves the legacy `client` cycle. |
| 07 | `07-migration-roadmap.md` | Phase-by-phase plan, expanding the user's 6-phase strategy into concrete steps. |
| 08 | `08-class-mapping.md` | Legacy file → new file/class table. |
| 09 | `09-technical-risks.md` | Risks specific to this migration. |
| 10 | `10-compatibility-considerations.md` | What must stay byte-identical for the Angular frontend and for HelloTalk upstream contracts. |
