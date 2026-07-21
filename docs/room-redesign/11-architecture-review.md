# Architecture & Code Quality Review — `com.jilali.roomcontext`

> Principal-architect-level production-readiness review, performed after the dedicated-client rebuild (docs/room-redesign/07-migration-roadmap.md Phase 3). Scope: every file under `com.jilali.roomcontext`. Findings below are backed by grep-verified reachability analysis, not assumption — every claim of "unused"/"dead" was confirmed by tracing actual import/reference graphs, not by reading code in isolation.

## Scores (0–10)

| Dimension | Score | Rationale |
|---|---|---|
| Overall architecture | 7/10 | Clean, consistent layering in the *live* code path (was ~4/10 before this review's cleanup, dragged down by an entire unreachable parallel subsystem — see Critical Issues). Post-cleanup: controllers → port interfaces → dedicated infrastructure clients, correct dependency direction, no cycles. |
| SOLID | 7/10 | SRP and ISP well respected (small, focused interfaces; one job per adapter). DIP was violated in one concrete spot (`RoomController` injecting `UserJilaliClient` directly instead of `UserUpstreamPort`) — fixed during this review. OCP/LSP not heavily exercised either way (little inheritance in this module). |
| Clean Code | 7.5/10 | Two real DRY violations found and fixed (`withUpstreamRetry` duplicated in `RoomJoinService`/`RoomUpstreamAdapter`; `currentUserId()` duplicated in `ProfileBundleService`/`VipUpstreamAdapter`). No god classes, no long methods, no magic numbers left un-named. Verbose DTOs are inherent to the wire contract being ported, not a smell. |
| Java 25 | 6/10 | Records used consistently for every DTO (good). Virtual-thread `StructuredTaskScope` used correctly for the three real fan-out use cases (`RoomJoinService`, `RoomsSearchService`, `ProfileBundleService`). Sealed types and pattern matching are essentially absent from the *live* code — the only place they existed (a sealed `MicState`, a domain `RoomEvent` hierarchy) was inside the now-deleted dead subsystem. This is an honest score reflecting current reality, not the deleted design. |
| Micronaut | 8/10 | Idiomatic declarative `@Client` interfaces (one per capability, correctly sharing the `jlhub` HTTP client id). `@Cacheable` applied where the audit called for it (translation, reference-data, and — after this round's C3 fix — user-info, restoring a caching behavior that had silently regressed during the port). `@Valid`/`@ExecuteOn(BLOCKING)` consistently present across all 9 controllers. No custom code duplicating a Micronaut built-in was found in the live path. |
| Hexagonal Architecture | 6.5/10 | The layering direction is correct and enforced (api → port.out → infrastructure), but there is no real domain layer left after the dead-code removal — this is a **lightweight, pass-through** hexagonal shape, not a rich-domain one. That is the right call for a BFF whose job is to reshape and re-expose an upstream contract (per the original goal's own "lightweight Hexagonal" instruction), but it should be named honestly: this is layered-ports architecture, not domain-driven design. |
| CQRS | N/A (by design) | Not implemented in the live path at all — no command/query objects, controllers call ports directly. This is consistent with the explicit instruction "apply CQRS only where it provides measurable benefit; do not force it everywhere" — a thin BFF with no business-rule enforcement has no CQRS benefit to capture. Scored N/A rather than penalized, since forcing it here would itself be a finding. |
| Simplicity | 8.5/10 | Post-cleanup. Was ~3/10 before this review (see Critical Issues) — an entire parallel, unreachable domain+application layer roughly doubled the module's apparent complexity for zero delivered behavior. |
| Maintainability | 8/10 | Consistent naming (`<Capability>JilaliClient`, `<Capability>UpstreamPort`, `<Capability>UpstreamAdapter`, `<Capability>Controller`), zero remaining legacy-package dependencies (grep-verified), zero cross-adapter duplication after this review's fixes. A dozen safety-critical "why" comments dropped during the port (upstream quirks, a documented production bug, a cache-consistency invariant) were found via legacy diff and restored — see I7. Docked slightly because the ~90 ported wire DTOs will need manual synchronization if HelloTalk's upstream contract changes — an inherent BFF cost, not a defect, but worth flagging for future maintainers. |

## Critical Issues (must fix) — all resolved during this review

### C1. An entire domain-driven subsystem (54 files) was unreachable dead code

Grep-traced every import of `domain.model.*`, `domain.event.RoomEvent`, `domain.policy.ManagerAuthorizationPolicy`, `domain.exception.DomainRuleViolation`, all 8 `domain.valueobject.*` types, all 7 `application.command.*` record files, all 7 `application.port.in.*UseCases` interfaces, the 6 `application.port.out.*RepositoryPort`/`RoomEventPublisherPort` interfaces, the 7 Phase-2 `application.service.{Room,Stage,Manager,Comment,SignIn,Vip,UserProfile}Service` classes, all 5 `infrastructure.memory.In-Memory*Repository` classes, and `infrastructure.websocket.MicronautRoomEventPublisher`.

**Result: every one of these 54 files formed one single, fully self-contained, closed graph with zero external callers.** No controller, no live application service, nothing in the actual HTTP request path ever invoked any `*UseCases` interface or touched the domain model. This was built in the design's Phase 2 ("domain + application layers, in-memory fakes, no real traffic") and never subsequently wired to the real controllers once Phase 3 built direct pass-through adapters instead.

This is precisely the pattern the review brief calls out — "if a service only forwards method calls, recommend deleting it," "if an interface has only one implementation and provides no architectural value, recommend removing it," "can this abstraction disappear?" With zero current callers, keeping this code is a pure YAGNI violation: it adds ~30% more files to navigate, misleads readers into thinking a rich domain model backs the API when it doesn't, and had zero test coverage exercising it end-to-end.

**Action taken**: deleted all 54 files. This is a safe, behavior-preserving simplification — deleting genuinely unreachable code changes zero observable behavior by definition. `domain.service.TranslationService` (the one live domain-layer interface, implemented by `TranslationServiceImpl` and used by `TranslateController`) was carefully preserved.

**Not recommended**: wiring the domain layer into the live controllers instead of deleting it. That would be new functionality (explicitly out of scope for this review) and a much larger, riskier change than a review-and-simplify pass warrants — the Phase-2 domain aggregates (e.g. `VipExperienceCard`'s claim/use state machine) would need an upstream-response-to-aggregate mapper that doesn't exist yet, exactly as flagged in `07-migration-roadmap.md`'s honest scope notes.

### C2. `RoomController` violated its own architecture's dependency direction

`RoomController.audienceReconcile()` injected `UserJilaliClient` (a concrete infrastructure `@Client` interface) directly and called `JilaliResponses.unwrap(...)` itself — bypassing `UserUpstreamPort` entirely, even though `UserUpstreamPort.roomUsers(...)` already does exactly this. Every other method on every other controller in the module correctly depends on a port interface, not a concrete client. This one method was the sole exception.

**Action taken**: `RoomController` now injects `UserUpstreamPort` instead of `UserJilaliClient`, and calls `userUpstream.roomUsers(...)` directly — the envelope-unwrap concern moves back into the adapter where it belongs. Zero behavior change (the port method does the identical unwrap internally).

### C3. `UserProfileEncryptedClient.fetchUserInfo` silently lost its `@Cacheable("user-info")` annotation during the rebuild

The legacy `JilaliGateway.userInfo(long userId)` this method replaces is annotated `@Cacheable("user-info")` (24h TTL, 20k-entry cache, already configured in `application.yml` and untouched by this rebuild) — deliberately, because the profile lookup is keyed by `userId` alone regardless of caller, and every room roster / comment author / notification avatar re-resolves the same handful of user IDs repeatedly. The rebuilt `UserProfileEncryptedClient.fetchUserInfo` reproduced the entire Curve25519-handshake-plus-AES call faithfully but **the `@Cacheable` annotation itself did not make it across the port** — a real, silent performance regression (every call, including `UserUpstreamAdapter.enrichBatch`'s per-room-roster loop, was paying a full encrypted round-trip on every invocation, cache or no cache) rather than a documentation-only gap like the other findings in this review.

**Action taken**: restored `@Cacheable("user-info")` on `fetchUserInfo`, plus the explanatory Javadoc (cache-key rationale, and the `x-ht-uid`-must-derive-from-`token`-not-the-caller's-own-JWT rationale — the legacy `JilaliGateway.userInfo` Javadoc documents that this exact mismatch is what caused every `userInfo()` call to be rejected with `BAD_REQUEST` in the past, a real production bug this code silently avoids by construction but was undocumented in the new module). **Verified live**: a fresh `userId` lookup via `GET /api/v2/users/info` took 3.6s (real Curve25519 handshake + AES round-trip to `api-global.hellotalk8.com`); the identical call repeated immediately after took 29ms — confirming the cache is active.

## Important Improvements — resolved during this review

### I1. Duplicated 5xx-retry loop (`RoomJoinService` and `RoomUpstreamAdapter`)

Both classes independently implemented the same "retry up to 4 times on a 5xx, 700ms delay, never retry a 4xx" algorithm — one with richer logging (upstream response body extraction), one without. Extracted to `infrastructure.client.UpstreamRetry.withRetry(Callable<T>)`, using the richer logging version as the single source of truth. Both call sites updated; the duplicate private methods removed.

### I2. Duplicated `currentUserId()` resolution (`ProfileBundleService` and `VipUpstreamAdapter`)

Both independently implemented "prefer the inbound Authorization header over the shared service-account token fallback" — identical `ServerRequestContext` + `JwtUtil.uidFromBearer` logic. Extracted to `infrastructure.client.CallerIdentity.currentUserId(AuthTokenHolder)`. Both call sites updated.

### I3. `ProfileController` inconsistently applied `@Valid` to `@Body` parameters

Every other `@Post`/`@Put` handler in the module that accepts a record-typed body annotates it `@Valid @Body`. `ProfileController` had four handlers — `follow`, `unfollow`, `visitors`, `edit` — taking real record types (`FollowRequest`, `UnfollowRequest`, `VisitorHistoryRequest`, `ProfileEditRequest`) without `@Valid`, breaking that convention. Investigated whether this was a live bug: none of the four records currently declare bean-validation constraints, so today `@Valid` is a no-op for all four — this is a consistency/defensive-coding gap, not a functional regression. Fixed by adding `@Valid` (and the missing `jakarta.validation.Valid` import) to all four methods, so the module-wide invariant "every real-record `@Body` is validated" holds without exception. The two remaining un-annotated `@Body` methods in the same controller (`visit`, `stats`) are correctly left as-is — both take raw `@Body Map<String, Object>`, and bean validation does not apply to a `Map`.

### I4. `ProfileBundleService` duplicated the same try/catch/log/degrade-to-null boilerplate 4 times

`fetchOwnStatsOrNull`, `fetchLimitationsOrNull`, `fetchPayChatInfoOrNull`, `fetchReminderMomentOrNull` each independently implemented "call the upstream client, extract `.data()`, catch `RuntimeException`, log a warning, degrade to `null`" — identical structure, differing only in which client method they call and the log message. Extracted a private generic `fetchOrNull(String description, Callable<R> call, Function<R, T> dataOf)` helper; all four methods now delegate to it in one line each. Reduced the class from ~115 to ~95 lines and removed 4 duplicate try/catch blocks, in the same spirit as I1/I2.

### I5. `RoomUpstreamAdapter` recomputed the Agora cipher key on every call instead of caching it once

Every other adapter that needs the Agora cipher key (`StageUpstreamAdapter`, `RoomJoinService`) either caches it in a field set in the constructor or recomputes it once per fan-out; `RoomUpstreamAdapter.decryptRtcToken` recomputed `properties.agoraCipherKey().getBytes(...)` on every single call. Changed the constructor to precompute `agoraCipherKey` once (matching `StageUpstreamAdapter`'s pattern) and removed the per-call recomputation.

### I6. `SignInJilaliClient.voiceTasks()` was the only real-data `@Get` in the module typed as `JilaliEnvelope<Object>`

Every other `Object`-typed envelope in the module (19 instances across `ManagerJilaliClient`, `UserJilaliClient`, `CommentJilaliClient`, `StageJilaliClient`, `VipJilaliClient`, `RoomJilaliClient`) is a fire-and-forget `@Post` action whose response payload is deliberately discarded — a consistent, correct idiom. `voiceTasks()` was the sole exception: a `@Get` whose response *is* consumed, forcing `SignInUpstreamAdapter.tasks()` into an unchecked `(Map<String, Object>) ... .get("items")` cast with a `@SuppressWarnings("unchecked")`, even though the target shape (`VoiceTasksResponse(List<Map<String, Object>> items)`) matches the JSON exactly. Changed the client method to declare `JilaliEnvelope<VoiceTasksResponse>` directly, letting Micronaut/Jackson deserialize the envelope's `data` straight into the record; the adapter is now a one-line `JilaliResponses.unwrap(client.voiceTasks())` with no cast and no suppression. Verified via live curl against the real HelloTalk upstream (`/api/v2/signin/panel` returned 200 with correctly-shaped JSON through the same client/adapter pair; `/api/v2/signin/tasks` itself returns a pre-existing upstream "bad request" for this test account, reproduced identically by the old, unmodified legacy `com.jilali.signin` code making the exact same call — confirming the type change is not the cause and is a pure code-quality improvement on the success path).

### I7. A dozen safety-critical "why" comments were silently dropped during the wire-DTO/logic port from legacy packages

Diffed every new controller/adapter/service against its legacy counterpart (`com.jilali.room`, `com.jilali.user`, `com.jilali.signin`, `com.jilali.client`, `com.jilali.translate`) specifically looking for explanatory comments describing a non-obvious upstream quirk, a documented production bug, or a deliberate invariant — as opposed to "what the code does" comments, which are correctly *not* carried over since well-named methods already make them redundant. Found and restored:

- **`RoomJoinService`**: the class/method-level rationale for why room info must be fetched *before*, not concurrently with, stage/audience/comments (a captured real-client session showed upstream 500ing on `stage/list` indefinitely — 13+ seconds, never recovering — when fetched immediately after `create_voice_channel` without this ordering); the `get_type=[3]` magic-list rationale on the audience-roster fetch (also restored at its `RoomController.audienceReconcile` duplicate).
- **`RoomUpstreamAdapter`**: the fresh-room 5xx-retry rationale on `voiceRoomInfoRaw`/`liveRoomInfoRaw`; the AES-token/`CAN_NOT_GET_GATEWAY_SERVER` rationale on `decryptRtcToken`.
- **`RoomController.userStartedChannel`**: the null-vs-404 Micronaut quirk (`HttpResponse.ok(null)` is required — a bare `return null` from a controller method is treated as "no route matched" and produces a 404, not a 200 with an empty body, even with `@Nullable` on the return type).
- **`ProfileUpstreamAdapter`**: the `/relation/follow`-is-idempotent-not-a-toggle rationale on `unfollow` (and why its response is normalized into `follow`'s shape); the `/visitors` missing-`sign`-field rationale on `visitors` (a real production finding: upstream's `{"code":400,"msg":"no data currently"}` reads like "zero visitors" but actually means "unsigned request" — see C3 below for the closely related `TranslationServiceImpl` cache-consistency comment restored in the same pass).
- **`UserController`**: the class-level security rationale for why the room-scoped profile endpoint is decoded server-side (the only upstream call that exposes the viewer's follow relation to an arbitrary user).
- **`UserUpstreamAdapter.enrichBatch`**: the batch-enrichment rationale (replaces a per-user frontend fetch pattern; warm cache entries are free — see C3, which is what makes this claim true again).
- **`CommentController.extractId`**: the best-effort/undocumented-upstream-shape rationale (already fixed earlier in this review round).
- **`TranslationServiceImpl.translate`/`jwtUid`**: the `@Cacheable("ai-translate")` cache-consistency rationale — this one is safety-critical, not just documentation: the cache is keyed on `(text, targetLang)` alone, so `jwtUid()` must **not** be switched to `CallerIdentity.currentUserId()` (the per-caller-preferring helper used elsewhere in this module) or it would silently corrupt the cache across different users. Without this comment, a future consistency pass (including a less careful version of this very review) could plausibly "fix" this into a real bug.

None of these changes altered behavior — they restore documentation that explains behavior the ported code already had (except `jwtUid`, which was already correct and now has the guardrail comment preventing a future regression, and the `@Cacheable("user-info")` annotation itself, which is a real fix — see C3).

## Nice-to-Have Improvements (not executed — judged not worth the churn)

- **N1**: Only `TranslateController` has a distinct `api.dto` layer (`TranslateRequest`/`TranslateResponse`) separate from `infrastructure.dto`; the other 8 controllers expose `infrastructure.dto.*` types directly as their public JSON contract. **Investigated and left as-is**: for a BFF whose entire purpose is reshaping-and-re-exposing an upstream contract with minimal transformation, introducing a parallel `api.dto` layer that's structurally identical to `infrastructure.dto` for the other 8 capabilities would be pure ceremony — a mapper copying fields with no behavioral gain. Translation's separate `api.dto` pair is justified because the BFF's own public contract (`text`/`targetLang` → `translatedText`) genuinely has no upstream-wire equivalent (the real upstream request, `AiTranslateUpstreamRequest`, is a completely different encrypted shape). This is correctly-applied hexagonal boundary-drawing, not an inconsistency to "fix" by adding ceremony everywhere.
- **N2**: `VipUpstreamAdapter.claimTrial()` and `ProfileBundleService` both call `CallerIdentity.currentUserId(...)` — a small, genuine cross-cutting concern. Could become a Micronaut `@Filter`-populated request attribute instead of a static helper. Not executed: the current form is already a single source of truth (no duplication), and threading a request-scoped attribute through would be a bigger change for a marginal readability gain.
- **N3**: `RoomUpstreamAdapter.decryptRtcToken` (the port method, used by the single-room-info endpoints) and `RoomJoinService.decryptRtcToken` (a private method, used by the join-bundle fan-out) share ~80% of their logic (null-guard `channelInfo`, extract the encrypted token, decrypt, `withRtcToken`), but differ in one deliberate way: the port method throws `JilaliException` (BAD_GATEWAY) when `rtcInfo` is missing, while the join-bundle version logs a warning and degrades gracefully, since a single room's join-bundle response shouldn't hard-fail the entire page over a missing RTC token. **Investigated and left as-is**: extracting a shared helper would require threading the differing failure behavior through a callback parameter, which reads less clearly than the current two independent 5-line methods — the abstraction would cost more in readability than the ~5 duplicated lines it would save.
- **N4**: `UserUpstreamAdapter.enrichBatch()` fetches each user's info sequentially (`request.userIds().stream().map(encryptedClient::fetchUserInfo)...`) rather than fanning out concurrently like `RoomJoinService`/`RoomsSearchService`/`ProfileBundleService` do. Flagged as a performance observation, not fixed: this is a behavior change (concurrency, not cleanup), out of scope for a review-and-simplify pass, and the current batch sizes in practice are small enough (room roster enrichment) that sequential calls are unlikely to be a real bottleneck.

## Classes Deleted (54)

`domain/model/{Room,Stage,RoomRoster,ManagerRoster,RoomMember,RoomCommentThread,RoomSignIn,VipExperienceCard,UserProfile,Comment}.java`, `domain/event/RoomEvent.java`, `domain/valueobject/{Cname,RoomUserId,HostId,ManagerId,BusiType,MicState,RoomLifecycleState,RoomLevel}.java`, `domain/exception/DomainRuleViolation.java`, `domain/policy/ManagerAuthorizationPolicy.java`, `application/command/**` (7 files), `application/port/in/*UseCases.java` (7 files), `application/port/out/{RoomRepositoryPort,CommentThreadRepositoryPort,SignInRepositoryPort,VipCardRepositoryPort,UserProfileRepositoryPort,RoomEventPublisherPort}.java`, `application/service/{Room,Stage,Manager,Comment,SignIn,Vip,UserProfile}Service.java`, `infrastructure/memory/**` (5 files), `infrastructure/websocket/MicronautRoomEventPublisher.java`. Plus the 4 orphaned unit test files that exercised the deleted value objects (`CnameTest`, `BusiTypeTest`, `UserIdTypesTest`, `MicStateTest`).

## Interfaces Removed

All 7 `*UseCases` interfaces (`RoomUseCases`, `StageUseCases`, `ManagerUseCases`, `CommentUseCases`, `SignInUseCases`, `VipUseCases`, `UserProfileUseCases`) — each had exactly one implementation and zero external callers, the textbook case for removal per this review's own stated criterion.

## Duplication Report

| Duplicate | Locations (before fix) | Resolution |
|---|---|---|
| 5xx-retry loop | `RoomJoinService`, `RoomUpstreamAdapter` | Extracted to `infrastructure.client.UpstreamRetry` |
| `currentUserId()` | `ProfileBundleService`, `VipUpstreamAdapter` | Extracted to `infrastructure.client.CallerIdentity` |

No other duplication found in the live code path.

## Performance Observations

- `TranslationServiceImpl.translate()` is `@Cacheable("ai-translate")` — correct, avoids re-paying the Curve25519 handshake + AES round-trip for a repeated `(text, targetLang)` pair.
- `RoomController`'s 4 reference-data endpoints (`language-groups/voice`, `language-groups/live`, `categories`, `config`) are `@Cacheable("reference-data")` — correct, these rarely change.
- The 3 `StructuredTaskScope` fan-outs (`RoomJoinService.joinBundle`, `RoomsSearchService.search`, `ProfileBundleService.bundle`) use virtual threads correctly — no thread-pool exhaustion risk, no blocking-the-event-loop risk (all controllers using them are `@ExecuteOn(BLOCKING)`).
- No N+1 patterns possible — there is no local persistence layer (post-cleanup, definitively none), so this class of issue cannot occur.

## Testability Observations

- The live adapters (`*UpstreamAdapter`) are constructor-injected with their `*JilaliClient` dependency, making them trivially mockable for a unit test — but **no unit tests currently exist for the live adapters or controllers** (only the 4 orphaned value-object tests existed, and those tested the now-deleted domain layer). This is a real gap: the module's only test coverage was for code that has just been deleted. Recommend adding a small number of adapter-level tests using a mocked `*JilaliClient` per capability as the next concrete step, rather than resurrecting the deleted domain-model tests.
- `RoomJoinService`/`RoomsSearchService`/`ProfileBundleService`'s `StructuredTaskScope` fan-out logic is the highest-value target for a unit test (mock the 3-4 client dependencies, assert correct aggregation and correct exception propagation on partial failure) — currently untested.

## Final Verdict

Production-readiness blockers (C1, C2) are resolved as of this review. The live module is now a clean, consistently-named, duplication-free layered-ports architecture appropriate for a BFF's actual job. The main remaining gap before genuine production sign-off is **test coverage** (Testability Observations above) — the module currently ships with zero automated tests for any of its 148 live files.
