# Phased Refactoring Roadmap

> Phase 1 → Phase N. Each phase is a self-contained, mergeable increment with concrete acceptance criteria. Security-critical fixes are first so a security review on the PR isn't invalidated by an architectural change.

## Sequencing rationale

The single biggest risk in any migration is **breaking the diff baseline for security review**: if we refactor the architecture and then later fix a missing auth check, the security team has to re-review both changes together. So Phase 1 is exclusively the security fixes that should land in-place, BEFORE the rewrite.

Then the architectural blockers (Phase 2) — both of which gate the entire rewrite — get their own PR.

Then the bulk of the work (Phase 3-4-5) can iterate safely.

---

## Phase 1 — Security fixes (in-place, no refactor)

**Goal**: close the 4 CRITICAL security gaps before any other work.

| Task | Where | Acceptance criterion |
|---|---|---|
| 1.1 Encrypt the `jwt` column at rest in `auth_session` | `auth/JdbcAuthSessionRepository.java`, `auth/AuthSchemaInitializer.java` | Migration: column encrypted, plaintext column dropped; new reads decrypt transparently. Old plaintext rows are re-encrypted on first read. Add `expired_at` + scheduled reaper. |
| 1.2 Add authorization to `manager.ManagerController` | `manager/ManagerController.java` | Each endpoint verifies the inbound caller can act on `(cname, host_id)` — either via `@Secured` with a `@RequiresOwnedByRoom` annotation, OR via an explicit docstring + upstream-guarantee contract. **Test:** forge an inbound request from a different user-id; the action is rejected. |
| 1.3 Add authorization + idempotency to `vip.VipExperienceCardController` | `vip/VipExperienceCardController.java` | Per-endpoint ownership check + `X-Idempotency-Key` header on `use`/`receiveFriendCard`. |
| 1.4 Verify and close `stage.StageController` authorization (likely absent) | `stage/StageController.java` | Focused review + same pattern as 1.2. |
| 1.5 Add `@Valid` to every `@Body`-annotated controller parameter (consistency hardening) | All `*Controller.java` files | Per-file verification — many places already partially have it. |

**Phase 1 exit criteria**:
- [ ] All 4 critical security findings have either an explicit fix merged OR an in-tree documenting comment with sign-off.
- [ ] An adversarial test in the BFF's test module proves each fix is effective.
- [ ] `git log` since Phase 0 shows ONLY security fixes (no architectural changes yet).

---

## Phase 2 — Architectural blockers (the gate to the whole rewrite)

**Goal**: make it possible for `client/` to compile in isolation and for any feature to be packaged independently.

| Task | Files | Acceptance criterion |
|---|---|---|
| 2.1 Move wire-only DTOs from feature packages to `platform.upstream-dtos` | `comment.dto.*`, `manager.dto.*`, `room.dto.*` (the wire-bearing subset), `signin.dto.*`, `stage.dto.*`, `user.dto.*` (the wire subset), `vip.dto.*` (the wire subset) | DTOs whose ONLY consumer is `client.JilaliClient`-style upstream interfaces are moved to `platform.upstream-dtos`. DTOs whose consumer is the Angular frontend stay where they are. |
| 2.2 Split `JilaliClient` into per-feature declarative interfaces | New: `client.RoomClient` (in `feature.room.api`), `client.StageClient` (in `feature.stage.api`), etc. Existing `ProfileClient`, `VipExperienceCardClient` move to their feature packages. | `client.JilaliClient` is deleted. Each feature package has its own narrow `@Client` interface. The Angular-facing response DTOs (if any) re-export as needed. |
| 2.3 Verify no cycle remains | `gradle compileJava` and a structural test that imports `com.jilali.client.PlatformClient` (the new abstract port) WITHOUT any feature | The latter passes. |

**Phase 2 exit criteria**:
- [ ] `client/` package is empty (or contains only the shared port interface, if any).
- [ ] Every feature package compiles against only `platform.*` + its own files.
- [ ] No feature-DTO is imported by any other feature package or by a port/interface.

---

## Phase 3 — Architecture consolidation

**Goal**: collapse the duplicated packages, replace custom infra with Micronaut-native.

| Task | Files | Acceptance criterion |
|---|---|---|
| 3.1 Unify `im` and `realtime` into a shared `feature.chat` package | Rename `im/` → `feature.chat.im`, `realtime/` → `feature.chat.room`. Add `feature.chat.shared.UpstreamWebSocketConnector<TEvent>` base. | Both packages' connectors delegate to the shared base. |
| 3.2 Replace `core/ws/ExponentialBackoff` + `core/ws/HeartbeatPump` + `core/ws/SequentialSender` with `@Retryable` + `@Scheduled` + a lightweight per-connector send queue | `core/ws/*.java` | The 3 helpers are removed. Connectors use Micronaut's `@Retryable` + `@Scheduled`. |
| 3.3 Replace `Sinks.Many.multicast()` pub-sub with `ApplicationEventPublisher` + `@EventListener` | `im/ImEventSource`, `realtime/RoomEventSource` | Subscribers use `@EventListener`. The first-opens/last-closes lifecycle moves to `@Context`/`@PreDestroy`. |
| 3.4 Split `room/RoomController` into `RoomLifecycleController`, `RoomSearchController`, `CategoryTopicController`, `RoomAudienceController` | `room/RoomController.java` | `RoomController` is deleted; the 4 new controllers each have ≤ ~5 endpoints and a single coherent purpose. |
| 3.5 Normalize imperative `@Client("jlhub") HttpClient` callers to declarative `@Client` interfaces | `auth/HelloTalkAuthClientImpl.java`, `client/JilaliGateway.java` (or wherever it lands post-Phase 2) | Both files use declarative `@Client` only. |
| 3.6 Break up `core/` into `platform.{filters, errors, websocket, config, auth-token, validation}` | All files in `core/` | Move and re-export; no code change beyond imports. |
| 3.7 Add per-cache Caffeine config to `application.yml` | `application.yml` | `user-info` and `ai-translate` caches have explicit size limits / TTL. |

**Phase 3 exit criteria**:
- [ ] `im/` and `realtime/` packages are gone (unified into `feature.chat`).
- [ ] `core/ws/` is gone.
- [ ] Every `*Controller` has ≤ ~5 endpoints.
- [ ] No imperatively-injected `@Client("jlhub") HttpClient` outside of test code.

---

## Phase 4 — DTO consolidation (mechanical)

**Goal**: collapse the 5 highest-value duplications.

| Task | Files | Acceptance criterion |
|---|---|---|
| 4.1 Collapse `Comment` ↔ `CommentDto` (28 fields) | `comment/dto/{Comment, CommentDto, CommentListDto, CommentListResponse}.java` + the 2 mappers in `CommentController` and `RoomJoinService` | Single record + custom Micronaut Serde timestamp serializer. Both mappers deleted. |
| 4.2 Lift `RewardItem` (signin == room) | `signin/dto/RewardItem.java`, `room/dto/RoomLevelConfigResponse.RewardItem.java` | Single shared `platform.models.RewardItem` record; both packages reference it. |
| 4.3 Consolidate `user` DTO cluster into ~8 distinct shapes | `user/dto/*` (36 files → 8-10 files) | After move: every visible DTO represents one concept. |
| 4.4 Replace the 7 stage `*Request` DTOs with a sealed `StageAction` interface + per-action records | `stage/dto/*Request.java` | One `sealed interface`, 7 records. |
| 4.5 Replace the 4 manager `*Request` DTOs with a sealed `ManagerAction` interface | `manager/dto/*Request.java` | Same pattern. |
| 4.6 Consolidate `UserOnlineStatus`/`HostStatus`/`UserStatus` | `user/dto/*` | One status record. |
| 4.7 Add `@Valid` (and field-level constraints) to every inbound request DTO | All `*Request` files | Compilation passes; `micronaut-validation` enforces at the seam. |

**Phase 4 exit criteria**:
- [ ] Total DTO count is ~½ of current (~80 → ~40 files).
- [ ] No code manually maps DTO→DTO across packages (verified via grep for `new <UpstreamRecord>` in service-layer code).

---

## Phase 5 — Micronaut-native cleanup, migration to reactive, modern Java 25 polish

| Task | Acceptance criterion |
|---|---|
| 5.1 `TranslateService` SSE → Micronaut reactive streaming | `Flux<ByteBuffer>` consumed incrementally; `@Cacheable` still works for completed lookups. Memory bounded. |
| 5.2 Convert `core/JilaliException` + `core/ApiError` + filters to a sealed `com.jilali.platform.errors.Error` with `@Error` annotation handler | One error contract, not competing mechanisms. |
| 5.3 Per-request uid caching for `ProfileController`/`ImSendController`/`JilaliGateway` | The 3 JWT decodes per request become 1. |
| 5.4 Apply `sealed interface` + `record patterns` to remaining dispatch points | `HtImNotifyMapper`'s `switch` on `msg_type` → pattern switch on a sealed `JsonMsg`. |
| 5.5 Verify all manual lifecycle state machines are exhaustive-safe | Connection state machines in connectors moved to sealed enum + switch. |

---

## Phase 6 — Final consolidation

| Task | Acceptance criterion |
|---|---|
| 6.1 Drop redundant `micronaut-jackson-databind` if `micronaut-serde-jackson` is sufficient | Smaller runtime footprint. |
| 6.2 Add `@PackageInfo` overview Javadoc to every platform + feature package | Documentation is part of the source tree. |
| 6.3 Final cross-package dependency regression test | Automated check that no feature package imports from another. |

---

## Acceptance criteria for "rewrite done"

- [ ] All Phase 1-6 exit criteria met.
- [ ] No P0/P1/P2/P3 entries remain in `docs/audit/reports/technical-debt.md`.
- [ ] Per-package dependency direction: feature → platform only, no feature→feature cycles.
- [ ] Every public interface is `sealed` (where applicable) and uses pattern-switching for dispatch.
- [ ] Every wire-layer HTTP client is declarative (`@Client` interface, no imperative `HttpClient` injection).
- [ ] Every long-running WebSocket uses `StructuredTaskScope` or equivalent for its lifecycle operations.
- [ ] Every unauthorized request to `manager`/`vip`/`stage` is rejected at the auth filter, not making it into the service layer.
- [ ] The `audit` markdown tree can be read in sequence as a navigable knowledge base (which it already is — this reorg should not delete it).

---

## Estimated effort per phase (informational, not a commitment)

| Phase | Relative effort | Risk |
|---|---|---|
| 1 (security) | Small — local changes, no architectural impact | Low (just be correct) |
| 2 (gateway blockers) | Medium — moving DTOs and splitting interfaces is mechanical | Medium (touches many imports) |
| 3 (architecture consolidation) | Large — biggest single phase | Medium (regression risk in WS connectors) |
| 4 (DTO consolidation) | Medium — mechanical | Low (regression caught by tests/Angular) |
| 5 (Micronaut-native polish) | Medium | Medium (behavioral changes, mostly in Translate) |
| 6 (final) | Small | Low |

Phase 3 carries the highest risk because the largest behavioral surface (WS connectors) is being restructured. Phase 4 carries the lowest per-PR risk because each DTO consolidation is reversible (re-introduce the dropped field if the Angular frontend complains).

Plan for periodic "stop and re-audit" gates after each phase: rerun the per-package auditors and compare against the current state to verify nothing regressed and to update this audit tree.
