## Purpose

Capped exponential backoff with full jitter: `delay = random(0, min(cap, base * 2^attempt))`. Full jitter is the standard defense against reconnect storms against a recovering upstream. Stateful only in the attempt counter; call `reset()` after a successful reconnect.

Promoted from `com.jilali.core.ws.ExponentialBackoff` in Refactor 4. Establishes the new `com.jilali.platform.reconnect` sub-package and a single source of truth for the two upstream WebSocket connectors (`im/HtImUpstreamConnector`, `realtime/HtLiveHubUpstreamConnector`).

## Public API

- `public final class ReconnectStrategy` (immutable surface; mutable internal state)
- `public ReconnectStrategy(Duration base, Duration cap)`
- `public Duration nextDelay()` — advances the attempt counter; full jitter on the upper bound.
- `public void reset()` — call after a successful reconnect so the next unrelated failure starts from `base`.
- Package-private `static Duration boundFor(int attempt, Duration base, Duration cap)` — testable.

## Dependencies

- **Inbound**: `im/HtImUpstreamConnector` (constructor + `nextDelay()` + `reset()`) and `realtime/HtLiveHubUpstreamConnector` (same).
- **Outbound**: java.time.Duration, java.util.concurrent.ThreadLocalRandom, java.util.concurrent.atomic.AtomicInteger.

## Coupling and cohesion

Single responsibility. Compact. Now lives in `platform/` (the target-structure landing zone) instead of `core/`, so feature packages can depend on it via the correct dependency direction (feature → platform, never the inverse).

## Code smells

- **Mutable `AtomicInteger` state inside an otherwise-immutable-feeling class** — could expose state as a `record` with the attempt counter separately, but the counter is internal detail and not part of the public contract; the current shape is the right balance.

## Technical debt

None — the only follow-up is the eventual consolidation into the `UpstreamWebSocketConnector<TEvent>` base class (Phase 3 in the audit's roadmap), at which point this becomes an instance field of the base.

## Duplicate logic

None — single source of truth (was the duplicate before Refactor 4 lifted it).

## Dead or unused code

None.

## Java 25 modernization opportunities

- Could become a `record` if the state were pushed into the connector (the connector already holds the attempt count "conceptually" via `intentionalClose`); a future Phase 3 refactor will do this.
- The two-arg constructor could be a factory: `ReconnectStrategy.standard()` for the common 1s→30s shape.

## Micronaut built-in opportunities

- Once the `UpstreamWebSocketConnector<TEvent>` base lands, this class becomes a `@Context`-scoped component and the `nextDelay()` / `reset()` methods are no longer called manually — Micronaut's `@Retryable(maxAttempts=Integer.MAX_VALUE, delay=...)` annotation replaces both. The class is then deletable.

## Refactoring recommendations

1. **Medium**: when Phase 3 lands, delete this class in favor of `@Retryable` on the base class's connect method.
2. **Low**: consider a `static standard()` factory for the common 1s→30s config (currently inlined in both callers).
