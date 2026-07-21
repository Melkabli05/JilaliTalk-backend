# Performance & Concurrency Observations

> Concrete thread-safety, virtual-thread, and scaling observations. Per-file/per-package detail in `docs/audit/files/**/*.md`.

## Already-modern concurrency (good)

- `room/RoomJoinService` and `signin/SigninController.roomLevelBundle` already use Java 25 `StructuredTaskScope` for parallel fan-out with automatic cancellation on any sub-task failure. This is the right primitive; generalize its usage during the rewrite.
- `core/ws/HeartbeatPump` runs on a per-task scheduler (uses virtual threads internally — verify during rewrite).

## Concrete issues

### 1. Hand-rolled reconnect loop in `HtImUpstreamConnector` and `HtLiveHubUpstreamConnector`
- Files: `im/HtImUpstreamConnector.java` lines 110-122, `realtime/HtLiveHubUpstreamConnector.java` — same idiom in both.
- Issue: manual `CompletableFuture.runAsync(..., CompletableFuture.delayedExecutor(...))` with manual backoff tracking in `core/ws/ExponentialBackoff`.
- Concern: race condition between `intentionalClose = true` (set by `close()`) and a retry execution that's about to fire (`reconnectInBackground()` does a check, but the check-then-fork gap is exposed).
- **Fix**: with the shared `UpstreamWebSocketConnector<TEvent>` base + Micronaut `@Retryable(maxAttempts=Integer.MAX_VALUE, delay=..., maxDelay=...)` + a `@PreDestroy`-cleaned `@Context` lifecycle, the race resolves naturally.

### 2. `volatile` flags used as state machines
- Files: `im/HtImUpstreamConnector` (fields `connected`/`intentionalClose`), `realtime/HtLiveHubUpstreamConnector` (same pattern).
- Issue: while each flag is individually `volatile`, their combinations can be transient (`intentionalClose=true` while a callback already enqueued a reconnect). Not a race-confirmed bug, but fragile.
- **Fix**: lift state machine to a sealed Java 25 enum (e.g. `Disconnected / Connecting / Connected / Reconnecting / Closing / ClosingFailed`) — exhaustive switch on state becomes the only way to mutate connection targets, which makes most race scenarios syntactically impossible.

### 3. SSE fully buffered in `translate/TranslateService.java`
- Lines that buffer: `client::postAiTranslate(...).body()` then parse whole body as a `String`.
- Issue: under load this is unbounded memory growth and slow TTFB (the upstream connection stays open but the response is held in heap before any processing starts).
- **Fix**: use Micronaut reactive `@Get(produces = TEXT_EVENT_STREAM)` on the client interface and stream-parse `SseChunk` lines as they arrive — `@Cacheable` stays for completed lookups.

### 4. `core/AuthTokenHolder` is `AtomicReference<String>` but accessors cache nothing else
- File: `core/AuthTokenHolder.java`.
- Observed: setter is `token.set(newToken)`, getter is `token.get()`. Each downstream caller re-decodes the JWT to extract `userId` (in `ProfileController.callerUserId()`, `ImSendController.callerUserId()`, `JilaliGateway.currentUserId()` — three independent JWT decodes per request).
- Concern: a single JWT decode per request is fine; THREE decodes per request (one per consumer) is gratuitous. The user's JWT for the request lifecycle should be decoded once per request and the uid cached.
- **Fix**: introduce a per-request cache (e.g. a `ThreadLocal<DecodedJWT>` populated by a request filter, or use Micronaut's `@RequestScope` bean). Each consumer reads `requestScopeUid.get()` instead of decoding.

### 5. `RoomEventSource.connectors` uses `ConcurrentHashMap` — good, but `audienceRevisions` increments on every roster-changing event
- File: `realtime/RoomEventSource.java` (audience revision counter).
- Issue: the revision counter is incremented on every push that "changes the roster" — fine, but it's exposed for client-side polling ("skip refetch if unchanged"). Without TTL or capped size on this map, memory grows with active rooms + revisits.
- **Fix**: add a `@Cacheable`-style TTL per `cname` for the audience revision map. Or at least ensure `RoomEventSource.close(cname)` removes the entry (verify this — likely already happens; confirm in the per-file doc).

### 6. `Client` interface declarative HTTP-client returns `Publisher`/`HttpResponse<byte[]>` depending on the call
- File: `client/JilaliClient.java` and friends.
- Issue: many methods return `HttpResponse<byte[]>` (raw bytes for `ht/encbin`) while others return typed DTOs — inconsistency between raw-bytes-pipe and decoded-response-pipe means the consumer side has to handle both shapes.
- **Fix**: standardize on typed response DTOs, with a small `@JsonSubTypes`-sealed wrapper for `ht/encbin` payloads if needed.

### 7. `@Cacheable("user-info")` and `@Cacheable("ai-translate")` — no per-cache config in `application.yml`
- Files: `user/ProfileBundleService.java`, `translate/TranslateService.java`.
- Issue: Caffeine-backed cache with no explicit size or TTL — runs with defaults. Under long-running deployment, cache could grow unbounded (especially `user-info` keyed by userId).
- **Fix**: add per-cache config under `micronaut.cache` in `application.yml`.

### 8. `core/ws/SequentialSender` uses `ReentrantLock`
- File: `core/ws/SequentialSender.java` — checked during normal operation, exposed for "serializing the next send."
- Status: legitimate use (sequentializing per-WebSocket writes is correct), but verify after the shared-base refactor that the lock granularity is correct.

### 9. `Realtime` vs `im` reconnect-and-decode race
- Files: `im/HtImUpstreamConnector`, `realtime/HtLiveHubUpstreamConnector`.
- Issue: when `close()` is called, `intentionalClose = true` and `ws.sendClose(1000, "normal")` — but if a reconnect is mid-flight (in `delayedExecutor`), the executor may still fire after we asked for clean shutdown, briefly opening a new WS.
- **Fix**: with `UpstreamWebSocketConnector<TEvent>` consolidating both, the lifecycle should use a per-instance `AtomicReference<State>` + `compareAndSet` to prevent any race; alternatively use Micronaut's reactive `@Context` bean lifecycle so `@PreDestroy` cleanly drains in-flight operations.

### 10. `JdbcAuthSessionRepository` is the one synchronous IO path
- File: `auth/JdbcAuthSessionRepository.java`.
- Issue: synchronous JDBC on a Micronaut controller thread (`auth.SessionAuthClientFilter` runs on client filter threads, default executor). Each call does a round-trip. With many in-flight requests this serializes.
- **Fix**: acceptable; this is a small local H2 file-store, not a perf-critical path. Note in the rewrite as low-priority.

---

## Summary

Most performance/concurrency issues are either:
- **Already mitigated** by `StructuredTaskScope` adoption in the obvious places
- **Caused by manual lifecycle state** (volatile flags, hand-rolled reconnect) — fixed by consolidation into a shared base + Micronaut-native lifecycle
- **Bounded by small local scope** (H2 sessions, single-tenant BFF) — not worth optimizing

The SSE-buffering issue (#3) and the multi-decode-per-request issue (#4) are the two real wins for performance.
