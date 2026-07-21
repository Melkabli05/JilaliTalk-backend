# HtLiveHubUpstreamConnector

`src/main/java/com/jilali/realtime/HtLiveHubUpstreamConnector.java` — `public class implements AutoCloseable`.

## Purpose
One LiveHub upstream WebSocket per room. Connects to `wss://uploadprocn.hellotalk8.com/livehub/ws/conn`, sends init/heartbeat/ack frames, decodes inbound frames, and fans them to room vs CC listeners. Unexpected close triggers an internal capped-exponential-backoff reconnect loop; `connect`'s future only reflects the first attempt.

## Public API
- `HtLiveHubUpstreamConnector(HtNotifyMapper, HtCcNotifyMapper, ObjectMapper)`.
- `void attach(Consumer<RoomRealtimeEvent> eventListener, Runnable disconnectListener)`.
- `void attachCc(Consumer<RoomCcRealtimeEvent>)` — optional CC listener; frames dropped if unset.
- `CompletableFuture<Void> connect(String userId, String cname, boolean isVisitor)`.
- `void close()` — sets `intentionalClose`, stops heartbeat, sends close.
- Inner: `Listener` (WS callbacks), `Session` record; helpers `handleFrame`, `sendHeartbeat`, `sendAck`.

## Coupling
Uses `com.jilali.core.ws` helpers (`ExponentialBackoff`, `HeartbeatPump`, `SequentialSender`), both mappers, DTOs. Constructed per-room by `RoomEventSource`.

## Notes
Structural parallel to `com.jilali.im.HtImUpstreamConnector` — same "connect WS upstream, heartbeat/ack, decode frames, fan out to listeners" pattern. Room version is plain-JSON (no binary framer/decoder that IM needs). `RoomEventSource` does not itself retry — this is the only backoff loop.
