## Purpose

Single source of truth for the "maintain a long-lived WebSocket with auto-reconnect" state machine. Extracted in Refactor 6 as a strict behavior-preserving step toward the Phase 3 `UpstreamWebSocketConnector<TEvent>` base class.

## Public API

- `public final class WebSocketConnectionLifecycle`
- `public WebSocketConnectionLifecycle(String name, ReconnectStrategy backoff)`
- `public void markOpening()` — open the connection (clear the close flag, reset backoff)
- `public void markClosed()` — mark the connection as cleanly closed (no further reconnects)
- `public boolean shouldReconnect()` — whether `reconnectInBackground` should still be active
- `public void reconnectInBackground(String logTag, Supplier<CompletableFuture<Void>> attempt)` — schedule a background reconnect with exponential backoff
- `public void resetBackoff()` — reset the backoff (call after a successful reconnect)
- `public String name()` — for log lines

## Dependencies

- **Inbound**: `im/HtImUpstreamConnector` (constructor + `markOpening` + `markClosed` + `resetBackoff`), `realtime/HtLiveHubUpstreamConnector` (same set).
- **Outbound**: `com.jilali.platform.reconnect.ReconnectStrategy`, `org.slf4j.Logger`, `java.util.concurrent.CompletableFuture`.

## Coupling and cohesion

Single responsibility: the state machine + the reconnect scheduler. Compact. Lives in `com.jilali.platform.websocket` (the target structure's web-platform sub-package).

## Code smells

- The `shouldReconnect()` method exposes the `intentionalClose` flag's negation. A `record` (e.g. `record LifecycleState(boolean isClosing)`) might express the state more cleanly, but the current shape is the smallest that fits the migration.

## Technical debt

- The full Refactor 7 work (per the audit's roadmap) replaces the connector's `intentionalClose` field and `reconnectInBackground` method with this lifecycle's versions. Until then, the IM connector still has its own `intentionalClose` field (kept so the listener's race-check at line 537 doesn't change). The LiveHub connector's field is similarly kept. This is a deliberate partial migration; a future Refactor 7 will remove the fields and route everything through the lifecycle.

## Duplicate logic

This is the deduplicated form. Before Refactor 6, the two connectors each held their own copy of this logic.

## Dead or unused code

None.

## Java 25 modernization opportunities

- Could use a sealed enum for connection state (`Connecting | Connected | Reconnecting | Closed`) and pattern-switch — but the current `volatile boolean` model is the minimum needed for the partial migration, and the eventual state-machine lift is part of Refactor 7.

## Micronaut built-in opportunities

- Once Refactor 7 lands, this class is deletable — `@Retryable(maxAttempts=Integer.MAX_VALUE, delay=...)` on the base class's `connect()` method subsumes the manual loop.
- Eventually `@Scheduled` could replace the per-connector heartbeat (`HeartbeatPump`) — but that's a separate, even larger refactor.

## Refactoring recommendations

1. **Refactor 7 follow-up**: complete the migration by removing the connectors' own `intentionalClose` fields and `reconnectInBackground` methods, routing them through this lifecycle.
2. **Refactor 8 follow-up**: delete this class when `@Retryable` is in place.
