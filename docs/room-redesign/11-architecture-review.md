# Architecture & Code Quality Review — `com.jilali.roomcontext`

> Principal-architect-level production-readiness review, performed after the dedicated-client rebuild (docs/room-redesign/07-migration-roadmap.md Phase 3). Scope: every file under `com.jilali.roomcontext`. Findings below are backed by grep-verified reachability analysis, not assumption — every claim of "unused"/"dead" was confirmed by tracing actual import/reference graphs, not by reading code in isolation.

## Scores (0–10)

| Dimension | Score | Rationale |
|---|---|---|
| Overall architecture | 7/10 | Clean, consistent layering in the *live* code path (was ~4/10 before this review's cleanup, dragged down by an entire unreachable parallel subsystem — see Critical Issues). Post-cleanup: controllers → port interfaces → dedicated infrastructure clients, correct dependency direction, no cycles. |
| SOLID | 7/10 | SRP and ISP well respected (small, focused interfaces; one job per adapter). DIP was violated in one concrete spot (`RoomController` injecting `UserJilaliClient` directly instead of `UserUpstreamPort`) — fixed during this review. OCP/LSP not heavily exercised either way (little inheritance in this module). |
| Clean Code | 7.5/10 | Two real DRY violations found and fixed (`withUpstreamRetry` duplicated in `RoomJoinService`/`RoomUpstreamAdapter`; `currentUserId()` duplicated in `ProfileBundleService`/`VipUpstreamAdapter`). No god classes, no long methods, no magic numbers left un-named. Verbose DTOs are inherent to the wire contract being ported, not a smell. |
| Java 25 | 6/10 | Records used consistently for every DTO (good). Virtual-thread `StructuredTaskScope` used correctly for the three real fan-out use cases (`RoomJoinService`, `RoomsSearchService`, `ProfileBundleService`). Sealed types and pattern matching are essentially absent from the *live* code — the only place they existed (a sealed `MicState`, a domain `RoomEvent` hierarchy) was inside the now-deleted dead subsystem. This is an honest score reflecting current reality, not the deleted design. |
| Micronaut | 7.5/10 | Idiomatic declarative `@Client` interfaces (one per capability, correctly sharing the `jlhub` HTTP client id). `@Cacheable` applied where the audit called for it (translation, reference-data). `@Valid`/`@ExecuteOn(BLOCKING)` consistently present across all 9 controllers (verified — one missing instance, on `TranslateController`, was already caught and fixed earlier in this session). No custom code duplicating a Micronaut built-in was found in the live path. |
| Hexagonal Architecture | 6.5/10 | The layering direction is correct and enforced (api → port.out → infrastructure), but there is no real domain layer left after the dead-code removal — this is a **lightweight, pass-through** hexagonal shape, not a rich-domain one. That is the right call for a BFF whose job is to reshape and re-expose an upstream contract (per the original goal's own "lightweight Hexagonal" instruction), but it should be named honestly: this is layered-ports architecture, not domain-driven design. |
| CQRS | N/A (by design) | Not implemented in the live path at all — no command/query objects, controllers call ports directly. This is consistent with the explicit instruction "apply CQRS only where it provides measurable benefit; do not force it everywhere" — a thin BFF with no business-rule enforcement has no CQRS benefit to capture. Scored N/A rather than penalized, since forcing it here would itself be a finding. |
| Simplicity | 8.5/10 | Post-cleanup. Was ~3/10 before this review (see Critical Issues) — an entire parallel, unreachable domain+application layer roughly doubled the module's apparent complexity for zero delivered behavior. |
| Maintainability | 7.5/10 | Consistent naming (`<Capability>JilaliClient`, `<Capability>UpstreamPort`, `<Capability>UpstreamAdapter`, `<Capability>Controller`), zero remaining legacy-package dependencies (grep-verified), zero cross-adapter duplication after this review's fixes. Docked slightly because the ~90 ported wire DTOs will need manual synchronization if HelloTalk's upstream contract changes — an inherent BFF cost, not a defect, but worth flagging for future maintainers. |

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

## Important Improvements — resolved during this review

### I1. Duplicated 5xx-retry loop (`RoomJoinService` and `RoomUpstreamAdapter`)

Both classes independently implemented the same "retry up to 4 times on a 5xx, 700ms delay, never retry a 4xx" algorithm — one with richer logging (upstream response body extraction), one without. Extracted to `infrastructure.client.UpstreamRetry.withRetry(Callable<T>)`, using the richer logging version as the single source of truth. Both call sites updated; the duplicate private methods removed.

### I2. Duplicated `currentUserId()` resolution (`ProfileBundleService` and `VipUpstreamAdapter`)

Both independently implemented "prefer the inbound Authorization header over the shared service-account token fallback" — identical `ServerRequestContext` + `JwtUtil.uidFromBearer` logic. Extracted to `infrastructure.client.CallerIdentity.currentUserId(AuthTokenHolder)`. Both call sites updated.

### I3. `ProfileController` inconsistently applied `@Valid` to `@Body` parameters

Every other `@Post`/`@Put` handler in the module that accepts a record-typed body annotates it `@Valid @Body`. `ProfileController` had four handlers — `follow`, `unfollow`, `visitors`, `edit` — taking real record types (`FollowRequest`, `UnfollowRequest`, `VisitorHistoryRequest`, `ProfileEditRequest`) without `@Valid`, breaking that convention. Investigated whether this was a live bug: none of the four records currently declare bean-validation constraints, so today `@Valid` is a no-op for all four — this is a consistency/defensive-coding gap, not a functional regression. Fixed by adding `@Valid` (and the missing `jakarta.validation.Valid` import) to all four methods, so the module-wide invariant "every real-record `@Body` is validated" holds without exception. The two remaining un-annotated `@Body` methods in the same controller (`visit`, `stats`) are correctly left as-is — both take raw `@Body Map<String, Object>`, and bean validation does not apply to a `Map`.

## Nice-to-Have Improvements (not executed — judged not worth the churn)

- **N1**: Only `TranslateController` has a distinct `api.dto` layer (`TranslateRequest`/`TranslateResponse`) separate from `infrastructure.dto`; the other 8 controllers expose `infrastructure.dto.*` types directly as their public JSON contract. **Investigated and left as-is**: for a BFF whose entire purpose is reshaping-and-re-exposing an upstream contract with minimal transformation, introducing a parallel `api.dto` layer that's structurally identical to `infrastructure.dto` for the other 8 capabilities would be pure ceremony — a mapper copying fields with no behavioral gain. Translation's separate `api.dto` pair is justified because the BFF's own public contract (`text`/`targetLang` → `translatedText`) genuinely has no upstream-wire equivalent (the real upstream request, `AiTranslateUpstreamRequest`, is a completely different encrypted shape). This is correctly-applied hexagonal boundary-drawing, not an inconsistency to "fix" by adding ceremony everywhere.
- **N2**: `VipUpstreamAdapter.claimTrial()` and `ProfileBundleService` both call `CallerIdentity.currentUserId(...)` — a small, genuine cross-cutting concern. Could become a Micronaut `@Filter`-populated request attribute instead of a static helper. Not executed: the current form is already a single source of truth (no duplication), and threading a request-scoped attribute through would be a bigger change for a marginal readability gain.

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
