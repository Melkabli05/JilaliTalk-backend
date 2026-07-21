# Micronaut Built-in Adoption Opportunities

> Where custom code in `jilalibff` reimplements something Micronaut already provides. Per-file evidence in the docs.

## Categories of opportunity

1. **Reconnect / retry** → Micronaut `@Retryable` / `io.micronaut.retry`.
2. **Periodic scheduling / heartbeat** → `@Scheduled`.
3. **Event pub-sub** → `ApplicationEventPublisher` + `@EventListener`.
4. **Declarative HTTP clients** → already partially adopted; normalize.
5. **WebSocket** → `@ServerWebSocket`/`@ClientWebSocket` (partial); reactive stream support for SSE.
6. **Configuration** → `@ConfigurationProperties` (already used).
7. **Validation** → `micronaut-validation` (dependency present, usage inconsistent).
8. **Security / authorization** → Micronaut Security if available (`@Secured`).
9. **Caching** → `@Cacheable` (already used in `ProfileBundleService`/`TranslateService`).
10. **Bean lifecycle / factories** → `@Singleton`, `@Factory`, `@Context` (already used).

---

## Per-area detail

### 1. Reconnect / retry

**Currently**: `htIm/HtImUpstreamConnector` and `realtime/HtLiveHubUpstreamConnector` each implement their own exponential-backoff reconnect loop, sharing `core/ws/ExponentialBackoff.java`.

**Micronaut offer**: `@Retryable` (or `io.micronaut.retry.annotation.Retryable`) on a thin reconnect operation, `Backoff` configurations.

**Refactor**: with the shared `UpstreamWebSocketConnector<TEvent>` base (already in the target architecture), give its `connect()` method `@Retryable(maxAttempts=Integer.MAX_VALUE, delay=..., maxDelay=...)` and remove the custom `ExponentialBackoff`. The `reconnectInBackground()` method becomes the one call that catches the final-exhaustion exception.

**Files affected**: `core/ws/ExponentialBackoff.java`, `HtImUpstreamConnector.java` (lines 92-122, the `attemptConnect`/`reconnectInBackground`), `RoomEventSource.java` (the same pattern).

**Benefit**: removes a class and ~50 lines of custom code; uses the well-tested Micronaut retry.

### 2. Heartbeat / scheduled tasks

**Currently**: `core/ws/HeartbeatPump.java` runs a periodic timer that calls `sendPing()` on a 30-second cadence. `HtImUpstreamConnector.start(HEARTBEAT_INTERVAL, this::sendPing)` is one of two callers.

**Micronaut offer**: `@Scheduled(fixedDelay = "30s")` on a method within a `@Singleton` bean.

**Refactor**: replace `HeartbeatPump` with a `@Scheduled`-annotated method on the upstream-connector class. Removes another utility class.

**Benefit**: removes a second custom utility.

### 3. Event publication / subscription

**Currently**: `realtime/RoomEventSource` and `im/ImEventSource` each implement a fan-out pub-sub via `Sinks.Many.multicast().directBestEffort()`. Subscribers (controllers, enrichers) call `subscribe()` to receive events.

**Micronaut offer**: `ApplicationEventPublisher<T>` for publishers; `@EventListener` for subscribers. This is the canonical Spring/Micronaut-style event-bus.

**Refactor**: replace `Sinks` with `ApplicationEventPublisher<ImRealtimeEvent>` / `ApplicationEventPublisher<RoomRealtimeEvent>`. Event-handler classes subscribe via `@EventListener`. The `subscriberCount` "first-opens / last-closes" lifecycle for the underlying connector can be replaced by Micronaut's standard `@Context`/`@PreDestroy` bean lifecycle.

**Benefit**: removes the in-proc lifecycle-management hacks tied to reference counts.

### 4. Declarative HTTP clients (normalize)

**Currently**: inconsistent — declarative `@Client` interfaces in `JilaliClient`, `ProfileClient`, `VipExperienceCardClient`, `HtTranslateClient`; imperative `@Client("jlhub") HttpClient` in `HelloTalkAuthClientImpl` and `JilaliGateway`.

**Micronaut offer**: declarative everywhere.

**Refactor**: convert imperative calls to declarative `@Client` interface methods. Per-call custom headers become `@Header` parameters or `@ClientFilter` methods.

**Benefit**: removes the "two styles" inconsistency; reduces per-call boilerplate.

### 5. WebSocket relay

**Currently**: `im/ImSocketController.java` and `realtime/RoomSocketController.java` are `@ServerWebSocket` classes that explicitly serialize events to text frames per subscriber.

**Micronaut offer**: `@ServerWebSocket` is already in use; the upgrade is to use Micronaut's reactive-streaming variant for fan-out (`reactor.core.publisher.Flux`) instead of manual `WebSocketSession.sendAsync`.

**Refactor**: convert manual `session.sendAsync(jsonString)` in per-loop emit paths to a `Flux<ImRealtimeEvent>` returned from a `@Get(uri="...")`-style handler with `produces = MediaType.APPLICATION_JSON_STREAM`.

**Benefit**: less manual session management, automatic backpressure.

### 6. Reactive SSE streaming

**Currently**: `translate/TranslateService.java` fully buffers SSE into a `byte[]`/`String` before parsing — no backpressure, no cancellation.

**Micronaut offer**: declare the upstream client method as `Flux<ByteBuffer>` or use a `@Get(produces = MediaType.TEXT_EVENT_STREAM)`-annotated reactive client method.

**Refactor**: declare upstream client as a `Publisher<byte[]>` returning `Flux<ByteBuffer>`, parse incrementally in the service.

**Benefit**: real backpressure, cancels when caller cancels, much less memory under load.

### 7. Validation — already partially adopted

**Currently**: `@Valid`/`@NotBlank`/`@NotNull` on some request DTOs (per the comment audit), inconsistent coverage.

**Micronaut offer**: `micronaut-validation` (already a dependency).

**Refactor**: add `@Valid` to every `@Body`-annotated controller parameter; add `@NotNull`/`@NotBlank`/`@Min`/`@Positive` etc. to every request DTO field where applicable.

**Benefit**: defends against malformed input at the seam rather than deep in the service layer.

### 8. Security / authorization

**Currently**: three controllers (`manager`, `vip`, likely `stage`) lack any role/ownership checks — see the security report.

**Micronaut offer**: Micronaut Security's `@Secured("roleName")` and `@Secured` rules; or a thin custom `@RequiresOwnedByRoom` annotation.

**Refactor**: introduce `@Secured` checks; if a fuller security model is needed, add the `micronaut-security-jwt` module to validate `Authorization` JWTs at the inbound filter level and propagate uid into the request scope.

**Benefit**: closes three real authorization gaps; makes future gaps visible at compile/edit time.

### 9. Caching — already adopted

**Currently**: `@Cacheable("user-info")` in `ProfileBundleService.java`, `@Cacheable("ai-translate")` in `TranslateService.java`. The doc on `TranslateService` explains its cache-key discipline correctly.

**Micronaut offer**: `micronaut-cache-caffeine` (already a dependency, with Caffeine backend).

**Improvement**: write per-cache configuration to `application.yml` (e.g. size limits, TTL); currently globally configured.

### 10. Bean factories / lifecycle

**Currently**: well-used already (`@Singleton`, `@Service`, `@Controller`, etc. throughout).

**Improvement**: where `RoomEventSource`/`ImEventSource` still open upstream connections themselves (instead of receiving them via DI), refactor to a `@Factory` that produces the connector as a bean and let its `@PreDestroy` handle cleanup.

---

## Summary

The codebase already uses `Micronaut` correctly in some places (`@Cacheable`, `@Valid`, `@ConfigurationProperties`, `@ServerWebSocket`, `@ConfigurationProperties`). The under-leveraged areas cluster around:
- Retry/reconnect (manual instead of `@Retryable`)
- Heartbeat scheduling (manual instead of `@Scheduled`)
- Event publication (manual Sinks instead of `ApplicationEventPublisher`)
- Authorization (no `@Secured`)

Conservative estimate: ~10-15 places where replacing custom infrastructure with Micronaut's built-ins saves real code AND reduces test surface.

The most leveraged opportunities (per Phase):
- **Phase 1 (security)**: add authorization via `@Secured` (or custom `@RequiresOwnedByRoom`).
- **Phase 2 (architecture)**: split `JilaliClient` into per-feature declarative sub-interfaces, normalize imperative→declarative HTTP.
- **Phase 3 (consolidation)**: extract `UpstreamWebSocketConnector<TEvent>`; convert manual reconnect/heartbeat to `@Retryable` + `@Scheduled`; SSE to reactive.
