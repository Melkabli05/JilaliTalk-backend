# RoomSocketController

`src/main/java/com/jilali/realtime/RoomSocketController.java` — `@Singleton @ServerWebSocket("/ws/ht/{cname}")`.

## Purpose
Push-only browser-facing WebSocket bridge. Each session subscribes to a room `cname` and receives serialized `RoomRealtimeEvent`s; opts into the subtitle stream via `?cc=1`. Frontend actions go through REST, never here.

## Public API
- `RoomSocketController(RoomEventSource source, ObjectMapper om, JilaliProperties properties)` — captures `allowedWebSocketOrigins`.
- `@OnOpen onOpen(cname, HttpRequest, WebSocketSession)` — origin allowlist check; subscribes room + (optional) CC streams, tracked per session id.
- `@OnMessage onMessage(cname, message)` — no-op (endpoint is push-only; required by Micronaut).
- `@OnClose onClose(cname, session)` — disposes room + CC subscriptions and unsubscribes from the source.
- Private `sendEvent` / `sendCcEvent` — serialize + `sendAsync`, guarded by `session.isOpen()`.

## Coupling
Injects `RoomEventSource`, `ObjectMapper`, `JilaliProperties`. Uses Micronaut WebSocket + Reactor `Disposable`.

## Notes
Structural parallel to `com.jilali.im.ImSocketController` — same "browser WS endpoint subscribes to an EventSource, relays serialized frames, disposes on close" pattern. Room version is per-`cname` and push-only (no inbound handling); IM has a separate `ImSendController` for outbound.
