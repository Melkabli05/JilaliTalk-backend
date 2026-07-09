# Backend architecture cleanup ("DDD migration") — design

## Context

The BFF is a Micronaut 5 / Java 25 proxy in front of the Jilali/HelloTalk API. It is
deliberately **not** a domain-modeling service today: no persistence, no business
invariants to protect — that state lives upstream in Jilali. The README documents this
as an intentional choice ("No repositories/no persistence... adding JPA here would be
overengineering").

That framing is correct for most of the codebase, but two real problems have grown
underneath it:

1. **Shared grab-bag objects.** `JilaliClient` (288 lines) is a single interface holding
   upstream method declarations for every feature — room, stage, comment, signin, vip,
   user. `JilaliGateway` (259 lines) was documented as "envelope-unwrapping plus the few
   calls needing real work," but has already absorbed real business logic
   (`claimVipTrial`/`ownsUnusedTrial` — VIP-perk selection rules) and infra plumbing
   (`userInfo`'s raw HTTP + Curve25519 handshake) into one singleton that no feature owns.
2. **Inconsistent orchestration placement.** Sometimes a dedicated service
   (`RoomJoinService`), sometimes inline in a controller
   (`RoomController.audienceReconcile` combines `RoomEventSource` + a client call
   directly), sometimes buried in the shared gateway (`claimVipTrial`). There's no rule
   for when orchestration earns its own service.

Concrete instances of debt found during review:
- `decryptRtcToken` is copy-pasted (with a behavior difference — one throws on missing
  RTC info, the other only logs) between `RoomController` and `RoomJoinService`.
- The 5xx-retry policy (`RoomJoinService.withUpstreamRetry`) is a room-specific class,
  but `RoomController` reaches into it via an adapter method
  (`withUpstreamRetryOrThrow`) for two unrelated single-call endpoints — a cross-cutting
  concern coupled to one feature's service.
- Comment DTO mapping (`toCommentDto`, `toMsgDto`, `toReplyInfoDto`) lives inside
  `RoomJoinService`, though it's "comment" domain translation, unrelated to joining a
  room.

Textbook DDD (entities, aggregates, repositories, domain events) does not fit this
codebase: there is no state to protect and no invariants beyond what Jilali already
enforces. A repository interface wrapping `JilaliClient` would have exactly one
implementation forever and would not fix any of the debt above unless the underlying
client is also split. Full ports-and-adapters with command/query handlers (a heavier
alternative also considered) is more rigor than a single-upstream, mostly-pass-through
proxy needs right now.

**Auth is explicitly out of scope for this migration.** A future login feature will call
the Jilali client for credential verification and add local SQLite-backed persistence,
which would be a legitimate place for real domain modeling — but that is separate,
later work. This migration does not touch `AuthController`, `auth/dto`, or the
`micronaut-jdbc-hikari`/`h2`/`bcrypt` build.gradle dependencies.

## Goals

Fix the debt found above by giving every proxy bounded context (`room`, `stage`,
`comment`, `vip`, `user`, `signin`, `im`, `realtime`, `manager`) a consistent, honest
shape:

- A **Controller** talks to at most one thing: a **Port** directly (pure single-call
  pass-through), or exactly one **Service** (>1 upstream call, a retry policy, or a
  business decision).
- A **Port** is a feature-scoped client interface (`RoomClient`, `StageClient`,
  `CommentClient`, `VipClient`, …) — one per bounded context, replacing the single
  `JilaliClient`. `ProfileClient`/`VipExperienceCardClient` already prove this pattern;
  this migration applies it uniformly.
- A **Service** owns orchestration and business rules for its own context and depends
  only on its own Port(s) — never reaches into another feature's service.
- **Mappers** (DTO translation) live in the package whose domain they translate.
- `core` holds only zero-feature-knowledge primitives: envelope unwrap
  (`JilaliResponses`, unchanged), the exception hierarchy (`JilaliException`, unchanged),
  auth/header propagation, and a new **generic upstream-retry decorator** extracted from
  `RoomJoinService.withUpstreamRetry`.
- `JilaliGateway` is retired. Its business logic and infra plumbing move into the feature
  packages that actually own them.

## Non-goals

- No entities, aggregates, or repositories for the proxy bounded contexts — there is no
  local state to protect.
- No CQRS / command-handler ceremony.
- No changes to `auth`, `auth/dto`, or the unused `micronaut-jdbc-hikari`/`h2`/`bcrypt`
  dependencies — future login work owns that.
- No behavior changes. This is a structural refactor: every endpoint must behave
  byte-identically before and after.
- No new automated tests written as part of this migration (see Verification below).

## Target shape (per bounded context)

```
Frontend ──HTTP──> XxxController                  (HTTP boundary, thin)
                       │
                       ├─ single call ──> XxxClient           (Port, @Client interface)
                       │
                       └─ orchestration/business rule ──> XxxService
                                                              │
                                                              ├─> XxxClient (own port)
                                                              ├─> core retry decorator (optional)
                                                              └─> XxxMapper (own DTO translation)
```

`core` package (cross-cutting only): `JilaliResponses`, `JilaliException`,
`GlobalErrorHandler`, header/auth filters, `JilaliProperties`, and the new retry
decorator.

## Migration phasing

Each phase is independently landable and reversible without touching contexts already
migrated. Order is smallest/lowest-risk first, ending with the largest context
(`room`/`stage`), then a final cleanup pass.

1. **`core`: extract the retry decorator.** Pull `RoomJoinService.withUpstreamRetry` out
   into a generic, reusable component in `core`. Behavior-preserving move; no callers
   change yet.
2. **`vip`: retire its slice of `JilaliGateway`.** Move `claimVipTrial`/
   `ownsUnusedTrial` into a new `VipService`. Smallest context touching the gateway —
   first proof the "retire `JilaliGateway` piece by piece" approach works.
3. **`user`: retire its slice of `JilaliGateway`.** Move the `userInfo` Curve25519/encbin
   plumbing into `user`. Riskier (raw HTTP + crypto), so tackled with focused manual
   verification of request-signing headers.
4. **`comment`: extract mapping.** Move `toCommentDto`/`toMsgDto`/`toReplyInfoDto` out of
   `RoomJoinService` into `comment`'s own mapper. Pure move, no behavior change.
5. **`room`/`stage`: the big one.** Split `JilaliClient` into `RoomClient`/
   `StageClient`; dedupe `decryptRtcToken` into one owned helper (resolve the
   throw-vs-log discrepancy deliberately, matching the stricter behavior); move
   `RoomController.audienceReconcile`'s inline orchestration into a proper service
   method; rewire `RoomJoinService` to use the `core` retry decorator from phase 1.
6. **Remaining contexts** (`signin`, `manager`, `im`, `realtime`, and whatever comment
   controller endpoints still hit the old client): split their slice of `JilaliClient`
   into their own port. Mechanical once the pattern is proven in phases 2–5.
7. **Delete `JilaliClient` and `JilaliGateway`** once nothing references them.

## Verification

There is no existing automated test suite (`src/test` is empty) and this migration does
not add one. Per-phase verification is:

- `./gradlew compileJava` after every move (catches wiring breaks immediately).
- Manual smoke check against the running app for every endpoint touched in that phase,
  driving the actual frontend flow — not just a typecheck.
- Behavior must stay byte-identical at each step. Any discovered behavior difference
  (e.g. the `decryptRtcToken` throw-vs-log discrepancy) is called out and resolved
  deliberately in that phase's commit message, not silently.

## Rollback

Each phase is its own commit or small commit series. Any phase can be reverted
independently since later phases only ever depend on earlier ones, never the reverse.
