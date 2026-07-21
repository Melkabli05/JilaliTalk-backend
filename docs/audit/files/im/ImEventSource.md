# ImEventSource

`src/main/java/com/jilali/im/ImEventSource.java` — `@Singleton public class`.

## Purpose
Singleton pub-sub bridge for the IM binary WebSocket channel. First browser subscriber opens the single upstream `HtImUpstreamConnector`; last to leave closes it. Pipes connector events through `ImEventEnricher` into a Reactor `Sinks.Many` multicast sink; replays the last connection-state to late subscribers. Also relays HTTP-originated outbound packets to the live connector. The DM counterpart of `RoomEventSource`.

## Responsibilities
- Reference-count subscribers (`AtomicInteger subscriberCount`); open/close the upstream on first/last.
- Build the connector with the *live* JWT (so post-relogin reconnects use the new token).
- Wire the connector's event listener through enrichment into the sink.
- Track and replay `lastConnectionState` to late joiners.
- Expose `sendOutbound(byte[])` for `ImSendController`.

## Public API
- `ImEventSource(JilaliProperties properties, AuthTokenHolder authToken, ObjectMapper om, ImEventEnricher enricher, @Nullable ImReloginRunner reloginRunner)` — **updated in Refactor 10**: `HelloTalkAuthClient authClient` replaced by `@Nullable ImReloginRunner`; the `hellotalkEmail`/`hellotalkPassword` fields are gone entirely (now owned by `ImReloginRunner`, sourced from `JilaliProperties` there instead of duplicated here).
- `Flux<ImRealtimeEvent> subscribe()`.
- `void unsubscribe()`.
- `void sendOutbound(byte[] data)`.
- Private: `emitAndTrackState`.

## Dependencies
- Injected: `JilaliProperties`, `AuthTokenHolder`, `ObjectMapper`, `ImEventEnricher`, `platform.reconnect.ImReloginRunner` (nullable — absent when relogin credentials aren't configured).
- Uses `UidExtractor`, constructs `HtImUpstreamConnector`, `ImRealtimeEvent`.
- Depended on by: `ImSocketController` (subscribe/unsubscribe), `ImSendController` (sendOutbound). Grep-verified.

## Coupling and cohesion analysis
Cohesive around "own the single upstream + fan events out." Because the IM channel is global (one account, one connection), state is simpler than `RoomEventSource`'s per-room maps — a single `connector`/`sink`/`lastConnectionState` triple. Coupling is moderate (5 injected deps, mostly passed through to the connector). The constructor resolves `userId` once via `UidExtractor.uidAsLong(authToken.get())`, capturing identity at bean creation.

## Code smells
- **Concurrency on subscribe/unsubscribe**: `subscriberCount` is atomic, but the compound "first? build sink+connector" (lines 68-103) and "last? null out connector+sink" (116-127) are not atomic as a whole. Two threads racing subscribe/unsubscribe at the 1↔0 boundary could interleave (e.g. a subscribe seeing `first=true` while a concurrent unsubscribe closes the connector it is about to use). Not guarded by a lock.
- **Constructor-captured `userId`**: if the token's uid changes (different account after relogin), `userId` is stale — mitigated because relogin keeps the same account, but a latent assumption.
- Mild **duplication** with `RoomEventSource` subscribe/first-subscriber pattern.

## Technical debt
- `directBestEffort()` sink drops events on backpressure with no signal — acceptable for a notification feed but undocumented as a policy at the emit site.
- The subscribe/unsubscribe ref-counting is hand-rolled; a shared connection-manager abstraction would remove the near-duplicate with `RoomEventSource`.

## Duplicate logic — comparison with `RoomEventSource`
Same "first subscriber opens upstream, last closes, replay last ConnectionState, multicast directBestEffort sink" pattern.

| Aspect | `ImEventSource` (global) | `RoomEventSource` (per-cname) |
|---|---|---|
| Keying | single connection | `Map<cname, …>` for connector/sink/count |
| Ref-count | one `AtomicInteger` | `Map<cname, AtomicInteger>` |
| Sink | one `Sinks.Many` | `Map<cname, Sinks.Many>` |
| Late-join replay | `lastConnectionState` field | `Map<cname, ConnectionState>` |
| Enrichment | yes (`ImEventEnricher`) | no |
| CC channel | n/a | yes (parallel ccSinks/ccCounts) |
| Outbound send | `sendOutbound` | none (room actions are REST) |

The lifecycle skeleton is duplicated; `RoomEventSource` is the multi-tenant generalization. A generic `SubscriptionHub<TKey, TEvent>` could host both. ~30-40 lines overlap.

## Dead or unused code
None. All public methods have external callers. Grep-verified.

## Java 25 modernization opportunities
- `emitAndTrackState`'s `instanceof ImRealtimeEvent.ConnectionState cs` (line 109) is pattern-matching — fine.
- Virtual threads not needed (Reactor already async).
- A sealed `Identifiable`/state model would help enrichment, not this class.

## Micronaut built-in opportunities
- **`ApplicationEventPublisher` / `@EventListener`**: the hand-rolled connector→enricher→sink pub-sub could partly use Micronaut's application-event bus, though Reactor `Flux` per-subscriber semantics (with replay + completion) are richer than the event bus offers; keep Reactor here.
- The single-upstream lifecycle could be a `@Context`/refreshable bean, but the lazy first-subscriber open is deliberate.

## Refactoring recommendations
1. Guard the subscribe/unsubscribe 1↔0 transition with a lock or `synchronized` to remove the ref-count race.
2. Extract a shared `SubscriptionHub<K,E>` reused by `RoomEventSource`.
3. Re-derive `userId` from the live token at connect time rather than capturing in the constructor (align with the relogin comment's intent).
