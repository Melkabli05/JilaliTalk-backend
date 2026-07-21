# `com.jilali.core` — cross-cutting infrastructure

## Purpose

The `core` package is the cross-cutting infrastructure layer for the whole backend. Holds everything that doesn't belong to a single feature: HTTP filter chain, global error handling, JWT/UID extraction, JSON envelope conventions, the live-mutable JWT holder, and small WebSocket helpers (backoff/heartbeat/sequential-sender) used by the IM/LiveHub connectors.

## File responsibilities (17 files)

| File | One-line summary |
|---|---|
| `ApiError.java` | Records describing error payloads returned to the Angular frontend. |
| `AuthTokenHolder.java` | Single, **live-mutable** JWT holder — supports auto-relogin by replacing the token in-place. Thread-safe via `AtomicReference`. |
| `CamelCaseResponseFilter.java` | HTTP-response filter converting outgoing JSON keys from snake_case to camelCase (Angular convention). |
| `DefaultHeadersClientFilter.java` | **Outbound** HTTP-client filter (order MAX) — sets HelloTalk device-fingerprint headers and derives `x-ht-uid` from the inbound JWT. The fallback tier of the auth ladder: per-user inbound → per-user session cookie → `AuthTokenHolder.get()`. |
| `DeviceIdStore.java` | Stable per-install device id persisted to disk, mimicking HelloTalk's own MMKV `DeviceVQHelper`. |
| `GlobalErrorHandler.java` | Last-resort `@Error` handler for uncaught exceptions. |
| `HeaderPropagationFilter.java` | Forwards a curated whitelist of inbound headers to outbound HTTP calls. |
| `JilaliEnvelope.java` | Marker interface for HelloTalk's `{code, msg, data}` envelope shape. |
| `JilaliErrorResponseFilter.java` | Outbound filter normalizing error envelopes before they leave the JVM. |
| `JilaliException.java` | Single exception type used as the "genuinely unexpected" signal (deliberately distinct from `LoginOutcome`/`SignupOutcome` which carry expected-failure data). |
| `JilaliProperties.java` | `@ConfigurationProperties("jilali")` record — single source of all config. |
| `JwtUtil.java` | JWT decoder utility. |
| `SnakeToCamelJson.java` | JSON-key conversion utility (probably used by filter or similar). |
| `UidExtractor.java` | Extracts the `uid` claim from a JWT. |
| `core/ws/ExponentialBackoff.java` | ⚠ Removed in Refactor 4 — promoted to `com.jilali.platform.reconnect.ReconnectStrategy` (single source of truth for the two upstream WebSocket connectors). |
| `core/ws/HeartbeatPump.java` | Helper firing periodic pings on a long-lived WebSocket. (Phase 3 candidate for replacement by `@Scheduled`.) |
| `core/ws/SequentialSender.java` | Helper serializing outbound writes on a shared WebSocket. |

## Dependencies

- **Inbound**: every other package depends on `core` — it's transitive infrastructure.
- **Outbound**: depends on `com.jilali.crypto` only (for any crypto-related filter work).
- Implicitly depends on every other package via Micronaut DI: classes here are injected by name into controllers/services across the project.

## Improvement opportunities

1. **High**: confirm whether `ApiError` records and `JilaliException` form **one** coherent error contract, or whether `JilaliErrorResponseFilter` and `GlobalErrorHandler` represent **two competing mechanisms** for the same concern (a known smell pattern in Micronaut apps that grew up without an upfront error policy).
2. **Medium**: the `core` package is itself a "dumping ground" — feature-cross-cutting concerns (filters, errors, JWT) share a namespace with WebSocket helpers (`core/ws`). The target architecture should split these: `com.jilali.platform.httpfilters`, `com.jilali.platform.errors`, `com.jilali.platform.websocket-support`. Avoids future drift.
3. **Medium**: `DefaultHeadersClientFilter` and `HeaderPropagationFilter` both manipulate outbound HTTP headers. If their concerns split between "HelloTalk-specific device fingerprint" and "browser-passed-through tokens", they should be cleanly separated. If they overlap, consolidate.
4. **Low**: `SnakeToCamelJson` may be redundant given `CamelCaseResponseFilter` already handles the conversion. Verify it's used in only one place.
