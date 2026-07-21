# SequentialSender

`src/main/java/com/jilali/core/ws/SequentialSender.java`

## Purpose
Serializes asynchronous sends over a single `java.net.http.WebSocket` so concurrent callers never interleave partial writes. Chains each send's `CompletableFuture<WebSocket>` after the previous one completes.

## Responsibilities
- Maintain a `CompletableFuture` chain; each `enqueue` appends a send that runs only after all prior sends complete.
- Route send failures to a per-send `onError` consumer without breaking the chain.
- `reset` the chain after a reconnect where in-flight sends are moot.

## Public API
- `synchronized void enqueue(Supplier<CompletableFuture<WebSocket>> sendOp, Consumer<Throwable> onError)` — queue a serialized send.
- `synchronized void reset()` — replace the chain with a completed future.
- `final` class; `volatile CompletableFuture<WebSocket> chain` field (private).

## Dependencies
- JDK only: `WebSocket`, `CompletableFuture`, `Supplier`, `Consumer`.
- Depended on by: `HtLiveHubUpstreamConnector`, `ImEventSource`, `HtImUpstreamConnector`.

## Coupling and cohesion analysis
High cohesion — a single concurrency primitive. No app coupling. `synchronized` guards chain mutation; `volatile` guards visibility. Correct and reusable across three consumers.

## Code smells
- Minor: `handle((r, t) -> null)` (line 20) swallows the previous send's result/error to keep the chain alive — intentional (each send has its own `onError`), but means a failure in one send is only observed via its `onError`, never re-thrown; acceptable and documented by intent.

## Technical debt
- Unbounded chain growth: `enqueue` keeps composing onto `chain`; while completed stages are GC-eligible, a fast producer against a stalled socket builds a long dependency chain. No backpressure. Note for high-throughput paths.

## Duplicate logic
None. Shared dedup point for serialized WebSocket sends across three consumers.

## Dead or unused code
None. `enqueue` and `reset` both used by connectors/event sources.

## Refactoring recommendations
- Consider a bounded queue / backpressure signal if a slow/stalled socket could accumulate many queued sends.
