# Realtime (`com.jilali.im` + `com.jilali.realtime`) hardening — design

## Problem

`com.jilali.im` and `com.jilali.realtime` power the two upstream WebSocket bridges to
HelloTalk (the personal `ht_im/sock` channel and the per-room LiveHub channel). They work —
both channels were just verified end-to-end with real traffic — but the code has grown
four concrete problems:

1. **God-class sprawl.** `HtImUpstreamConnector` is 624 lines doing ~10 unrelated jobs:
   WebSocket lifecycle, login-packet construction, heartbeat scheduling, binary frame
   parsing, QQTEA decryption, zlib inflate/deflate, JSON→event mapping for ~10 message
   shapes, offline-sync pagination, group-sync handling, and typing-indicator decoding.
2. **Duplication between the two connectors.** `HtImUpstreamConnector` and
   `HtLiveHubUpstreamConnector` each hand-roll an identical "chain sends through one
   `CompletableFuture` so they don't interleave" pattern and an identical "virtual-thread
   `ScheduledExecutorService` + reschedulable heartbeat `ScheduledFuture`" pattern.
3. **Fragile parsing/decoding.** `HtLiveHubUpstreamConnector` builds JSON frames via raw
   string concatenation (`"{\"user_id\":" + ...`); `HtImUpstreamConnector`'s decode logic is
   buried inside the god class with no independent test coverage (the only tests that exist
   today reach into the connector via relaxed method visibility).
4. **Missing resilience.** Neither connector retries on an unexpected upstream disconnect —
   a network blip just kills the connection until a browser client notices and re-subscribes,
   forcing a full teardown/rebuild of the downstream WebSocket too.

## Goals / non-goals

- **Goal:** decompose the god class into focused, independently-testable units.
- **Goal:** eliminate the duplicated send-queue and heartbeat-scheduling code via shared,
  composable utilities.
- **Goal:** replace fragile hand-built JSON strings with `ObjectMapper`-built payloads.
- **Goal:** add capped exponential backoff with full jitter for unexpected upstream
  disconnects, without changing how intentional teardown (last subscriber leaves) works.
- **Non-goal:** changing the wire contract. `/ws/im`, `/ws/ht/{cname}`, and the
  `ImRealtimeEvent`/`RoomRealtimeEvent` JSON shapes are frozen — this is a pure internal
  refactor. Zero frontend changes.
- **Non-goal:** merging the two connectors into one class or a deep inheritance hierarchy.
  The two protocols (binary vs. text framing, differing dispatch shapes) differ enough that
  composition of small shared utilities is a better fit than a shared abstract base.

## Design

### New shared infrastructure — `com.jilali.core.ws`

Cross-cutting concerns already live in `com.jilali.core` in this codebase (see
`JilaliProperties`, `UidExtractor`); the new WebSocket-support utilities follow that
convention under `com.jilali.core.ws`.

**`ExponentialBackoff`** — pure delay calculator, no sleeping, no I/O:

```java
public final class ExponentialBackoff {
    public ExponentialBackoff(Duration base, Duration cap) { ... }
    public Duration nextDelay(); // base * 2^attempt, capped, then full jitter; increments attempt
    public void reset();         // call on successful reconnect
}
```

Formula: `delay = min(cap, base * 2^attempt)`, then `Duration.ofMillis(ThreadLocalRandom
.current().nextLong(0, delay.toMillis() + 1))` (full jitter — the AWS-architecture-blog
standard, reconfirmed as current best practice: prevents synchronized reconnect storms
against a recovering upstream). Defaults for both connectors: base 1s, cap 30s.

**`SequentialSender`** — the duplicated send-chain pattern, generalized over the send
operation so both binary (`WebSocket.sendBinary`) and text (`WebSocket.sendText`) callers
share it:

```java
public final class SequentialSender {
    public void enqueue(Supplier<CompletionStage<WebSocket>> sendOp, Consumer<Throwable> onError);
}
```

**`HeartbeatPump`** — the duplicated scheduler pattern:

```java
public final class HeartbeatPump implements AutoCloseable {
    public HeartbeatPump(String threadName);
    public void start(Duration interval, Runnable pingAction);
    public void reschedule(Duration newInterval); // LiveHub's interval is server-driven and can change
    public void stop();
    @Override public void close(); // stop() + shutdown the executor
}
```

Each connector owns one `SequentialSender`, one `HeartbeatPump`, and one
`ExponentialBackoff` instance instead of hand-rolling the equivalent fields.

### Reconnect semantics

- **Intentional close** (`RoomEventSource`/`ImEventSource` call `connector.close()` because
  the last browser subscriber unsubscribed) sets an `intentionalClose` flag before closing
  the socket. The `Listener.onClose`/`onError` handlers check this flag: if intentional, they
  behave exactly as today (notify `disconnectListener`, no retry).
- **Unexpected close** (flag not set) schedules a reconnect via `ExponentialBackoff.nextDelay()`
  on the connector's own scheduler, re-invoking the same `connect(...)` parameters that were
  used originally (each connector already stores what it needs to reconnect: `userId`/`jwt`/
  `deviceId`/`deviceModel` for IM, `userId`/`cname`/`isVisitor` for LiveHub). `disconnectListener`
  is only invoked if reconnect attempts are abandoned (this refactor does not add a max-attempt
  cutoff — retries continue as long as there is a subscriber; `RoomEventSource`/`ImEventSource`
  still own the "is anyone listening" lifecycle via `close()`).
- On a successful reconnect, `ExponentialBackoff.reset()` is called so the next unrelated
  failure starts from the base delay again.

### `com.jilali.im` decomposition

`HtImUpstreamConnector` keeps: WebSocket lifecycle (`connect`/`close`/`Listener`), login-packet
send, and dispatch (which handler to call for F1/F2/F5) — everything else moves out:

**`HtImFrameDecoder`** (new, pure, no networking/state) — static methods taking raw bytes +
session key, returning decoded results:
- `decodeF1(bytes, payloadLen) -> DecodedF1` (login response / offline response / group
  response, discriminated by the caller's already-known `cmdId`)
- `decodeF2(bytes, payloadLen, sessionKey) -> DecodedF2` (read-receipt / poke / JSON push /
  unknown, after QQTEA decrypt + zlib inflate)
- `decodeTyping(bytes, payloadLen, sessionKey) -> boolean isTyping`
- `decodeOfflinePacket(base64, sessionKey) -> DecodedF2` (reuses the F2 decode path since the
  payload shape is identical once the outer base64/header layer is stripped)

Each decode result is a small sealed interface/record (e.g. `DecodedF2.Json(JsonNode)`,
`DecodedF2.ReadReceipt(String msgId)`, `DecodedF2.Poke`, `DecodedF2.Unknown(int firstByte)`)
so the connector's dispatch code stays a simple `switch` with no byte-manipulation inline.

**`HtImNotifyMapper`** (new) — `mapPushPayload`/`mapText`/`mapImage`/`mapGift`/`mapIntro`/
`mapNotify`/`mapProfileVisit`/`textOr` move here verbatim (this logic is correct and already
covered by `HtImUpstreamConnectorMappingTest` — the move is mechanical, not a rewrite).
Mirrors `HtNotifyMapper`'s existing shape in `com.jilali.realtime` (same pattern already
proven solid there). The connector holds one instance and calls `mapper.map(jsonNode, header)`.

**`HtImPacketFramer`** gains `inflate` and `copyPayload`, moved from the connector to live
next to their siblings `deflate`/`buildPacket`/`parseHeader`.

Net effect: `HtImUpstreamConnector` drops from 624 lines to roughly 200 — WS lifecycle,
login, heartbeat/send/backoff wiring via the shared utilities, and a thin dispatch layer.

### `com.jilali.realtime` polish

`HtLiveHubUpstreamConnector`:
- `initFrame()`/`heartbeatFrame()`/`ackFrame()` switch from string concatenation to
  `ObjectMapper.createObjectNode()...` (matches the safer pattern `HtImUpstreamConnector`
  already uses for its login packet).
- Adopts `SequentialSender`, `HeartbeatPump`, `ExponentialBackoff` in place of its hand-rolled
  equivalents.
- Otherwise unchanged — its `Session` record and delegation to `HtNotifyMapper` are already
  solid.

`HtNotifyMapper`, `RoomEventSource`, `RoomSocketController`, `RoomRealtimeEvent`,
`ImEventSource`, `ImSocketController`, `ImRealtimeEvent` — **untouched**. Already single-purpose
and already tested (or, for the `EventSource`/`SocketController` pairs, thin enough that
there's nothing to extract).

### Testing

- `ExponentialBackoffTest` (new) — assert delay bounds/growth/cap, and that `reset()` returns
  to the base range. Pure, no real waiting.
- `HtImFrameDecoderTest` (new) — F1 login/offline/group response parsing, F2 decrypt+inflate,
  read-receipt (`0x25`) and poke (`0x08`) detection, typing-indicator bit parsing, offline
  base64 packet decode. Built from the byte-level logic already in the current connector, so
  these are characterization tests for existing correct behavior, not new behavior.
- `HtImNotifyMapperTest` (renamed/moved from `HtImUpstreamConnectorMappingTest`) — the existing
  10 cases, now calling the mapper directly instead of via relaxed connector visibility.
- `HtNotifyMapperTest`, `RoomRealtimeEventTest` — unchanged, must stay green (proves the
  `realtime` package's behavior didn't shift).
- `SequentialSender`/`HeartbeatPump` are thin infrastructure wrappers around JDK concurrency
  primitives; covered indirectly through the connectors' existing integration behavior rather
  than dedicated unit tests, consistent with this codebase not unit-testing e.g. the raw
  `HttpClient` WebSocket plumbing today.

## Risks

- **Behavioral drift during extraction.** Mitigated by moving logic verbatim where possible
  and keeping all existing tests green before/after each extraction step.
- **Reconnect storms if `RoomEventSource`/`ImEventSource` are also retried at a higher layer.**
  Not a concern today — neither does that — but worth a one-line comment in the connector so a
  future change doesn't stack two independent backoff loops.
