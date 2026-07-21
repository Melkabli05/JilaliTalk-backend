# Technical Debt Report (Prioritized)

> Ranked by severity × ease-of-fixing. Numbered roughly P0 → P5, where P0 = security/blockers, P5 = cosmetic.

---

## P0 — security/correctness, fix before ANY further work

### T-1. Plaintext JWT at rest in `auth_session` table
- **Where**: `auth/JdbcAuthSessionRepository.java` line 45 (`INSERT INTO auth_session (..., jwt, ...)` writes the upstream JWT verbatim); `auth/AuthSchemaInitializer` (schema definition).
- **Why a problem**: filesystem read = live HelloTalk upstream credentials. Any file-read or DB-dump exposure becomes a credential disclosure.
- **Fix**: encrypt the `jwt` column at rest (AES-GCM with a server-side key, or use an external KMS). Add expired-row reaping so the table doesn't grow unbounded.
- **Benefit**: removes a credential-vault posture.

### T-2. `ManagerController` has no authorization checks
- **Where**: `manager/ManagerController.java` — no `@Secured`, no role check, no ownership comparison.
- **Why**: any authenticated caller can promote/demote moderators or approve manager operations.
- **Fix**: add Micronaut `@Secured` annotations + a `RoomModerator`/principal check that compares the inbound JWT's `uid` against `host_id` (or a moderator-list lookup).
- **Benefit**: matches what was likely intended behavior. May need to verify whether an upstream gateway is trusted to enforce this (if so, document explicitly in the controller).

### T-3. `VipExperienceCardController` has the same authorization gap
- **Where**: `vip/VipExperienceCardController.java`.
- **Why**: any authenticated caller can read/use any user's VIP card; no idempotency token on `use`/`receiveFriendCard` allows double-claim.
- **Fix**: same as T-2 (`@Secured` + ownership check), plus an idempotency-key header (Micronaut has built-in support via `@Idempotent` annotation in some library integrations; otherwise a simple `X-Idempotency-Key` header persisted to `vip/idempotency_keys`).
- **Benefit**: removal of a real money/privilege-grant exposure.

### T-4. `StageController` authorization likely absent (verify)
- **Where**: `stage/StageController.java` — same `(cname, userId)` pattern as `manager` controllers, no moderator check.
- **Fix**: same as T-2.

---

## P1 — architectural blockers, fix first within the rewrite

### T-5. Circular dependency `client ↔ 7 feature packages`
- **Where**: `client/JilaliClient.java` (imports from `comment/dto`, `manager/dto`, `room/dto`, `signin/dto`, `stage/dto`, `user/dto`) plus `client/ProfileClient.java`, `client/JilaliGateway.java`, `client/VipExperienceCardClient.java`. Inverse: every feature package imports `com.jilali.client`.
- **Why**: cannot compile `client` in isolation, cannot establish a hexagonal port-and-adapter boundary.
- **Fix**: split `JilaliClient` into per-feature sub-interfaces (lives in each feature package), move wire-only DTOs from `client` (or feature-DTOs) into the `client` package itself so both sides depend downward on shared wire types.
- **Benefit**: unblocks every other architectural improvement.

### T-6. `im` ↔ `realtime` structural duplication (~500 lines)
- **Where**: `HtImUpstreamConnector` ≈ `HtLiveHubUpstreamConnector`, `ImEventSource` ≈ `RoomEventSource`, `ImSocketController` ≈ `RoomSocketController`, plus the three mapper pairs.
- **Why**: parallel implementations of the same "maintain WS upstream, fan out, relay" pattern with subtle bug-prone variations. Two separate bug surfaces.
- **Fix**: extract `UpstreamWebSocketConnector<TEvent>` abstract base. Configure wire-format specifics (binary vs JSON) and lifecycle specifics (singleton vs per-key) via template-method or strategy composition.
- **Benefit**: ~500 lines deleted, one bug surface to maintain.

### T-7. `JilaliClient` is a God Interface (50+ methods)
- **Where**: `client/JilaliClient.java`.
- **Why**: too many concerns in one interface, must be split before Phase 4 anyway.
- **Fix**: split into `RoomClient`, `StageClient`, `ManagerClient`, `CommentClient`, `SigninClient`, `TranslateClient`, `VipClient`. Each in its feature package. Already-isolated interfaces (`ProfileClient`, `VipExperienceCardClient`) move to the same package as their feature-DTOs.

---

## P2 — DTO proliferation

### T-8. `user/dto/` 36 files → ~8 distinct shapes
- Most expensive is `UserInfo` ↔ `UserInfoRequest` ↔ `UserInfoResponse` ↔ `ProfileMeResponse` ↔ `ProfileBundleResponse` cluster — collapse to a single record + envelope.
- Status trio (`UserOnlineStatus` / `HostStatus` / `UserStatus`) — likely collapse.
- Follow/Unfollow mirror pair — share a base.
- **Fix**: introduce `user.dto.profile`, `user.dto.relationships`, `user.dto.presence`, `user.dto.batches` sub-packages with one record per concept.

### T-9. `comment/dto/Comment` ↔ `comment/dto/CommentDto` 28-field duplicate
- 28 fields, differ only in timestamp unit (sec vs ms) and one casing. Two hand-written mappers in `CommentController.toDto` AND `RoomJoinService.toCommentDto`.
- **Fix**: single record + custom Serde serializer for the timestamp.

### T-10. `signin/dto/RewardItem` ↔ `room/dto/RoomLevelConfigResponse.RewardItem` — exact 8-field clone
- **Fix**: lift `RewardItem` to a shared `com.jilali.platform.models.reward` package.

### T-11. `room/dto/{HostUser, RoomUser, UserBase}` — composition opportunity
- `UserBase` likely intended as the shared base.
- **Fix**: confirm and compose (or rename to clarify).

---

## P3 — code smell / testing / maintainability

### T-12. `client` imperative + declarative HTTP-client usage mix
- Imperative `@Client("jlhub") HttpClient` in `HelloTalkAuthClientImpl` and `JilaliGateway`; declarative `@Client` interfaces everywhere else.
- **Fix**: normalize on declarative where possible. Per-call custom header needs become declarative `@Header` parameters or a thin `@Filter`.

### T-13. Manual `WebSocket` reconnect/heartbeat reimplementation
- `HtImUpstreamConnector`, `HtLiveHubUpstreamConnector` both use `core/ws/ExponentialBackoff` + `core/ws/HeartbeatPump`; Micronaut has `@Retryable` and `@Scheduled`.
- **Fix**: reuse Micronaut's `@Retryable` and a `@Scheduled` heartbeat; remove `core/ws/ExponentialBackoff` and `core/ws/HeartbeatPump` (one already lives there).

### T-14. Manual SSE buffering in `TranslateService`
- `SseChunk` parsing is fully buffered — no backpressure, no incremental delivery.
- **Fix**: switch to a Micronaut reactive-streaming consumer.

### T-15. Manual envelope-unwrap repetition
- `JilaliResponses.unwrap` is the single utility but `*Controller` files sometimes do this directly; verify with a grep during the rewrite and migrate stragglers.

### T-16. Variable naming inconsistency: `userId` vs `user_id` vs `UserId`/`UID`
- Across DTOs and methods; pick one convention.

---

## P4 — larger refactor opportunities

### T-17. `core` package is itself a "dumping ground"
- Filters, JWT, errors, exceptions, JSON utilities, WebSocket helpers all share one namespace.
- **Fix**: split into `com.jilali.platform.http.filters`, `com.jilali.platform.errors`, `com.jilali.platform.security.jwt`, `com.jilali.platform.websocket`.

### T-18. `JilaliException`/`GlobalErrorHandler`/`ApiError`/`JilaliErrorResponseFilter` — one or multiple error mechanisms?
- Verify whether these compose into one coherent error contract or compete.

### T-19. Both `micronaut-serde-jackson` and `micronaut-jackson-databind` declared
- Decide: drop one. Likely the standard-jackson-databind (consolidate on `micronaut-serde-jackson`).

### T-20. `HttpStatus` typed wrapper behind `MessageAck.prefix`
- Currently `int` with implicit 0/non-zero convention; promote to enum for readability.

---

## P5 — cosmetic

- T-21. `@NotBlank` validation coverage is inconsistent across request DTOs (some validate all fields, some only `cname`).
- T-22. `card_type` type inconsistency in `vip` DTOs (`int` vs `String`).
- T-23. `expireAt: String` while sibling timestamps are `long` in `comment/dto`.
- T-24. Three `ReplyInfo` records disagree on `@JsonProperty` casing.
- T-25. TextMatcher should declare time complexity if it does anything beyond naive substring.

---

## Summary

- 4 P0 security items must be fixed immediately — at minimum before any further feature work.
- 3 P1 architectural blockers gate the entire rewrite (T-5 `client` circular dep + T-6 `im`/`realtime` dup + T-7 God Interface).
- 4 P2 DTO consolidations are the bulk of the work but each is mechanical.
- The remaining (P3-P5) is hygiene.

Estimated total cost: not all that bad once `client` is split — most DTO consolidations become "move the file, re-export" rather than logic changes. The hard part is sequencing security fixes (T-1 through T-4) before any architectural refactor, since refactors invalidate the diff baseline for security review.
