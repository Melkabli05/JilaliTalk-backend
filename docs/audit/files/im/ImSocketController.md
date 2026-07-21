# ImSocketController

`src/main/java/com/jilali/im/ImSocketController.java` — `@Singleton @ServerWebSocket("/ws/im")`.

## Purpose
Push-only browser-facing WebSocket endpoint that relays every `ImRealtimeEvent` from `ImEventSource` (the upstream IM channel) to connected browser sessions as JSON. The DM counterpart of `RoomSocketController`.

## Responsibilities
- On open: origin-check, subscribe to `ImEventSource`, forward events to the session, track the `Disposable`.
- On close: dispose the subscription, `unsubscribe()` from the source.
- Ignore inbound messages (push-only).
- Serialize events with `ObjectMapper` and send via `session.sendAsync`.

## Public API
- `ImSocketController(ImEventSource source, ObjectMapper om, JilaliProperties properties)`.
- `void onOpen(HttpRequest<?> request, WebSocketSession session)` (`@OnOpen`).
- `void onMessage(String message)` (`@OnMessage`).
- `void onClose(WebSocketSession session)` (`@OnClose`).
- Private `sendEvent`.

## Dependencies
- Injected: `ImEventSource`, `ObjectMapper`, `JilaliProperties` (for `allowedWebSocketOrigins`).
- Micronaut WebSocket annotations, Reactor `Disposable`.
- Depended on by: nothing in code (framework-invoked endpoint). Grep-verified sole references are within the file.

## Coupling and cohesion analysis
Small, cohesive relay. One dependency of substance (`ImEventSource`). Session→subscription tracking via `ConcurrentHashMap<String,Disposable>`. Clean single responsibility.

## Code smells
- **Backpressure**: `sendEvent` uses `sendAsync` fire-and-forget with no completion handling; combined with the source's `directBestEffort()` sink, a slow browser silently loses events. No queue-depth or send-failure backpressure — acceptable for notifications but undocumented.
- **Duplication** with `RoomSocketController` (near-identical onOpen/onClose/sendEvent), minus the per-cname and CC handling.
- Minor: origin check allows requests with a *null* Origin header (non-browser clients) through — intentional but worth noting.

## Technical debt
- Global `ImEventSource.unsubscribe()` on every session close: since the source is single-connection ref-counted, this is correct, but the controller has no per-session guarantee that its `onOpen` subscribe succeeded before `onClose` unsubscribes (if `onOpen` returned early on a rejected origin, `onClose` still calls `unsubscribe()`, decrementing the count without a matching increment). **Real bug risk**: rejected-origin open path (lines 44-48 returns before `source.subscribe()`), yet `onClose` unconditionally calls `source.unsubscribe()` — an unbalanced decrement. Cite lines 44-48 vs 68.

## Duplicate logic — comparison with `RoomSocketController`
| Aspect | `ImSocketController` | `RoomSocketController` |
|---|---|---|
| Path | `/ws/im` (global) | `/ws/ht/{cname}` (per room) |
| Origin check | identical | identical |
| Subscribe | `source.subscribe()` | `source.subscribe(cname)` |
| CC channel | none | opt-in `?cc=1`, second subscription |
| onClose | dispose + unsubscribe | dispose room + cc + unsubscribe |
| sendEvent | identical body | identical body (+ `sendCcEvent` clone) |

`onOpen` origin-guard, `onMessage` no-op, `sendEvent` serialization, and the `subscriptions` map are ~30 lines duplicated. A shared `abstract PushWebSocketController<E>` could host all of it.

## Dead or unused code
None. All methods framework-invoked (`@OnOpen`/`@OnMessage`/`@OnClose`). Grep-verified.

## Java 25 modernization opportunities
- Nothing significant; the class is small. Virtual threads not relevant (Micronaut manages the WS threads and sends are async).

## Micronaut built-in opportunities
- Already uses Micronaut's declarative `@ServerWebSocket` — correct.
- Could use Micronaut's `WebSocketBroadcaster` to fan events to sessions instead of manually tracking `Disposable`s and calling `session.sendAsync`, letting the framework own session bookkeeping. That would also fix the unbalanced-unsubscribe bug by decoupling per-session send from source ref-counting.
- Origin filtering could move to a Micronaut HTTP filter / CORS config rather than inline.

## Refactoring recommendations
1. **Fix the unbalanced `unsubscribe()`**: only call `source.unsubscribe()` in `onClose` if `onOpen` actually subscribed (track a per-session flag, or guard on `subscriptions.remove(id) != null`).
2. Extract shared `PushWebSocketController<E>` base with `RoomSocketController`.
3. Consider `WebSocketBroadcaster` to offload session management to Micronaut.
4. Document/measure the best-effort drop policy for slow clients.
