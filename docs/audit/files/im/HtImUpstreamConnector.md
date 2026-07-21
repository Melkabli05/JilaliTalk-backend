# HtImUpstreamConnector

`src/main/java/com/jilali/im/HtImUpstreamConnector.java` — package-private class, `implements AutoCloseable`.

## Purpose
Owns a single binary WebSocket connection to `wss://api-global.hellotalk8.com/ht_im/sock`. Handles login, 30s heartbeat, packet dispatch, offline/group sync, reconnect-with-backoff, and status-105 auto-relogin. Delegates byte decoding to `HtImFrameDecoder` and JSON→event mapping to `HtImNotifyMapper`; emits `ImRealtimeEvent`s to an attached listener. The DM counterpart of `HtLiveHubUpstreamConnector`.

## Responsibilities
- WebSocket lifecycle: `connect`/`attemptConnect`/`reconnectInBackground`/`close`, plus the inner `Listener` (onBinary framing, onClose reconnect, onError).
- Login packet construction (`sendLoginPacket`) and login-response handling (banned/status-105/session-key).
- Status-105 auto-relogin via `HelloTalkAuthClient` + `AuthTokenHolder` (`attemptRelogin`).
- Packet dispatch (`handlePacket` → `handleMsgAck`/`handlePush`/`handleTyping`/`handleF1`).
- Offline & group message sync with pagination (`sendOfflineSyncRequest`, `handleOfflineResponse`, `handleGroupResponse`).
- Outbound send for HTTP-originated packets (`sendOutbound`), heartbeat (`sendPing`).

## Public API
- `HtImUpstreamConnector(long userId, String jwt, String deviceId, String deviceModel, ObjectMapper om, HelloTalkAuthClient authClient, AuthTokenHolder authTokenHolder, String hellotalkEmail, String hellotalkPassword)`.
- `void attach(Consumer<ImRealtimeEvent> eventListener, Runnable disconnectListener)`.
- `CompletableFuture<Void> connect()`.
- `void sendOutbound(byte[] data)`.
- `void close()` (AutoCloseable).
- Everything else private, plus inner `class Listener implements WebSocket.Listener`.

## Dependencies
- Injected/collab: `HtImFrameDecoder`, `HtImNotifyMapper` (both `new`'d internally), `HelloTalkAuthClient`, `AuthTokenHolder`, `ObjectMapper`, `ApkSignatureGenerator`, `LoginResponse`.
- Shared infra: `com.jilali.core.ws.ExponentialBackoff`, `HeartbeatPump`, `SequentialSender`.
- Static import of all `HtImPacketFramer` builders/constants.
- JDK `java.net.http.WebSocket` client.
- Depended on by: `ImEventSource` (constructs, attaches, connects, closes, sendOutbound). Grep-verified.

## Coupling and cohesion analysis
This is the **most coupled and lowest-cohesion class in the batch**. It mixes: transport lifecycle, auth/relogin (HTTP, credentials), protocol dispatch, offline-sync pagination business logic, and login-JSON construction. Efferent coupling spans auth, crypto, framer, decoder, mapper, and three ws-infra helpers. It shares the connect/backoff/heartbeat skeleton with `HtLiveHubUpstreamConnector` (see duplicate section) but re-implements it independently.

## Code smells
- **God Class (moderate)**: ~550 lines spanning 5 responsibilities (transport, auth-recovery, dispatch, sync-business-logic, login-payload building).
- **Long Method**: `sendLoginPacket` (16-field JSON), `handleOfflineResponse`/`handleGroupResponse` (near-identical pagination bodies), `dispatchPush` switch.
- **Shotgun Surgery / Duplicate**: `handleOfflineResponse` vs `handleGroupResponse` differ only by field name (`packet_list` vs `msgs`) and next-page cmdId — a change to pagination touches both.
- **Feature Envy**: pagination logic (`has_more`/`last_id` interpretation) is upstream-protocol business logic sitting in the transport class.
- **Concurrency**: several `volatile` mutable fields (`ws`, `connected`, `intentionalClose`, `sessionKey`, `jwt`) mutated from listener threads, the reconnect common-pool thread, and `close()` — see Technical debt.

## Technical debt / concurrency
- **Reconnect vs close race** (concurrency): `intentionalClose` (line 82) and `ws` (line 80) are `volatile` but there is no happens-before ordering between `close()` setting `intentionalClose=true` and an in-flight `reconnectInBackground()`/`attemptConnect().thenAccept` that checks `intentionalClose` then assigns `this.ws=sock`. A close arriving between the check (line 118) and the assignment (line 123) can leave a live socket + heartbeat started after close. `attemptConnect` mitigates with a re-check but the window is not atomic. Cite: `attemptConnect` lines 117-129 vs `close` lines 146-156. Low probability (single-user), but real.
- **Relogin loop bound**: `attemptRelogin` (267-311) is well-isolated and *does* guard against infinite loops — it only fires on status 105, skips when credentials are blank, and gives up (`close()`) on a failed relogin rather than retrying. Design is sound; the residual risk is that a *successful* relogin that still yields status 105 (server keeps rejecting) reconnects and can re-trigger relogin — no attempt counter caps this specific ping-pong. Recommend an attempt ceiling.
- **`ImRealtimeEvent.GroupMessage`** wire path (`handleGroupResponse`) decodes group frames but the group *send* opcode is reserved — the placeholder event is **reserved for future feature**, not a bug.
- `new HttpClient` is a static singleton shared across connectors — fine.

## Duplicate logic — comparison with `HtLiveHubUpstreamConnector`
The two connectors are **structurally parallel** and independently implemented.

| Concern | `HtImUpstreamConnector` | `HtLiveHubUpstreamConnector` |
|---|---|---|
| Transport | `java.net.http.WebSocket`, binary | same client, text |
| Reconnect | `reconnectInBackground()` + `ExponentialBackoff(1s,30s)` | identical method + identical backoff |
| Heartbeat | `HeartbeatPump("im-hb")`, 30s fixed | `HeartbeatPump("livehub-hb")`, server-driven interval |
| Send | `SequentialSender` + `sendOutbound`/`sendBinary` | `SequentialSender` + `send` |
| Close | intentionalClose flag, sendClose(1000) | identical pattern |
| Listener | inner `Listener`, buffer-until-`last`, reconnect on close | identical structure (StringBuilder vs ByteArrayOutputStream) |
| connect() future | reflects first attempt only | same, documented identically |
| Frame handling | binary decode + mapper dispatch | text route to room/cc mapper |

`connect`/`attemptConnect`/`reconnectInBackground`/`close`/`Listener.onClose`/`onError` are ~90 lines that differ only in log strings and text-vs-binary buffering. This is the single largest de-duplication opportunity in the batch. The **auth/relogin block (~90 lines) is IM-only** and legitimately not shared.

## Dead or unused code
None dead. `CMD_GROUP_MESSAGE` (in framer) unused-but-reserved. `Listener.onError` framework-invoked. `sendOutbound` is the public send hook used by `ImEventSource.sendOutbound` ← `ImSendController`. Grep-verified.

## Java 25 modernization opportunities
- **Virtual threads**: the entire `CompletableFuture.runAsync(..., delayedExecutor)` reconnect chain and the blocking `authClient.login()` off-thread hop could be replaced by a virtual-thread task that simply `Thread.sleep(delay)` then reconnects — linear, readable, no delayedExecutor plumbing. `attemptRelogin`'s "must not block the WS listener thread" concern disappears with a `Thread.ofVirtual().start(...)`.
- `dispatchPush` already switches on the sealed `F2Push` — exemplary; keep.
- `handlePacket`'s cmdId/packetType if-chain (194-205) → `switch` on a `Command` enum (see framer doc).
- Model login payload as a `@Serdeable record` instead of hand-built `ObjectNode`.

## Micronaut built-in opportunities
- **`@Retryable` / `io.micronaut.retry`**: the hand-rolled `ExponentialBackoff` + `reconnectInBackground` loop overlaps with Micronaut's declarative retry. It cannot wholesale replace a *persistent* reconnect loop (retry annotations wrap a single method call, not an infinite background reconnect), but the *relogin* HTTP call and initial connect attempt are good `@Retryable` candidates.
- **`@Scheduled`**: the 30s heartbeat is a manual `HeartbeatPump`; a `@Scheduled(fixedRate="30s")` on a bean could drive it — though per-connection lifecycle makes the current pump acceptable.
- **`@ClientWebSocket`**: Micronaut's declarative WebSocket client could own the connection/lifecycle/reconnect for the *text* LiveHub side; for this *binary* side the raw JDK client is a closer fit, but a Micronaut-managed client would still remove hand-rolled request(1) backpressure plumbing.
- The class is **not a Micronaut bean** (constructed by `ImEventSource`); making the shared lifecycle a managed base bean would unlock `@Retryable`/`@Scheduled`.

## Refactoring recommendations
1. Extract a shared `abstract UpstreamWebSocketConnector` (or `UpstreamWebSocketConnector<TEvent>`) holding connect/backoff/heartbeat/close/Listener; leave frame-decode + login abstract. Shared with `HtLiveHubUpstreamConnector` — eliminates ~90 duplicated lines each.
2. Move offline/group pagination into a small `OfflineSyncPaginator` and merge the two near-identical handlers.
3. Cap relogin attempts to prevent 105-ping-pong; add an attempt counter.
4. Convert reconnect/relogin to virtual-thread linear code.
5. Guard the reconnect/close race with a single lock or a state enum (CONNECTING/CONNECTED/CLOSING/CLOSED) instead of scattered volatiles.
