# HeartbeatPump

`src/main/java/com/jilali/core/ws/HeartbeatPump.java`

## Purpose
Owns a single virtual-thread scheduler that runs one periodic heartbeat (ping) for a WebSocket. `start` cancels any previously-scheduled ping before scheduling a new one, so a server-driven interval change never leaks the old schedule. `AutoCloseable`.

## Responsibilities
- Create a single-thread virtual-thread scheduled executor named per instance.
- Schedule a fixed-rate ping (`start`), replacing any existing schedule.
- Stop/cancel the current ping (`stop`).
- Shut the scheduler down on `close`.

## Public API
- `HeartbeatPump(String threadName)` — constructor; creates the virtual-thread scheduler.
- `synchronized void start(Duration initialDelay, Duration period, Runnable pingAction)` — cancels current, schedules fixed-rate.
- `void start(Duration period, Runnable pingAction)` — convenience (initialDelay = period).
- `synchronized void stop()` — cancel current ping.
- `void close()` — stop + `shutdownNow`.

## Dependencies
- JDK concurrency: `ScheduledExecutorService`, `Executors.newSingleThreadScheduledExecutor`, virtual-thread factory.
- Depended on by: `HtLiveHubUpstreamConnector`, `HtImUpstreamConnector`.

## Coupling and cohesion analysis
High cohesion — one job (own a replaceable periodic ping). No app coupling. Thread-safety handled via `synchronized` on start/stop plus `volatile future`. Correctly uses virtual threads. Clean reusable primitive shared by both connectors.

## Code smells
- Minor: `future` is `volatile` but only mutated under `synchronized` (start/stop); the `volatile` is defensive for the private `cancelCurrent` read path. Harmless.
- `close()` is not `synchronized` while `stop()` is — `close` calls `stop()` (synchronized) then `shutdownNow()`, so ordering is fine, but a concurrent `start()` racing `close()` could schedule onto an already-shut-down executor (throws `RejectedExecutionException`). Low likelihood; note for lifecycle correctness.

## Technical debt
- No guard against `start` after `close` (would throw from the executor). A `closed` flag would make lifecycle misuse explicit.

## Duplicate logic
None. Shared dedup point for heartbeat scheduling across both connectors.

## Dead or unused code
- None. Grep-confirmed: the two-arg `start(Duration period, Runnable)` overload is used by `HtImUpstreamConnector:246`, and the three-arg form by `HtLiveHubUpstreamConnector:182`. Both `start` overloads, `stop`, and `close` are live.

## Refactoring recommendations
- Add a `closed` guard so `start` after `close` fails fast (or is a no-op) rather than throwing an executor rejection.
