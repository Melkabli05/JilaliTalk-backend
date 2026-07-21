# `com.jilali.im` + `im.dto` — personal 1:1 DM channel

## Purpose

Maintains the personal IM WebSocket to HelloTalk's `ht_im/sock` upstream, decodes binary push frames, maps notify events, and relays them to the browser over `/ws/im`. Also serves as the outbound-send path for 1:1 DMs (text/image/gift/introduction/voice_room/live_link) and helper events (typing-indicator, read-receipt).

## File responsibilities (8 root + 1 dto = 9 files)

### Root

| File | One-line summary |
|---|---|
| `HtImUpstreamConnector.java` | Single singleton binary WebSocket to HelloTalk's `ht_im/sock`. Login on connect, 30s heartbeat, reconnect with exponential backoff, status-105 auto-relogin (recently added via `AuthTokenHolder` + `HelloTalkAuthClient.login`). |
| `HtImPacketFramer.java` | Builds the 20-byte little-endian binary header + body for outbound sends (text, image, gift, voice_room, live_link, read receipt, typing indicator). |
| `HtImFrameDecoder.java` | Inverse: parses inbound 20-byte-header + body push packets. Defined as a `sealed interface` for exhaustive cmd-id dispatch. |
| `HtImNotifyMapper.java` | Decrypts + decompresses + maps a JSON push payload to `ImRealtimeEvent` variants. |
| `ImEventSource.java` | Pub-sub fan-out: first browser subscriber opens the upstream, last closes it. Uses `AuthTokenHolder` for the live upstream JWT. |
| `ImSocketController.java` | Browser-facing `@ServerWebSocket` relaying serialized events to subscribed tabs. |
| `ImSendController.java` | REST handlers (`/api/im/messages/{userId}/read|typing|send`) for browser-driven outbound sends — delegates the actual byte-level frame construction to `HtImPacketFramer` and pushes through `ImEventSource.sendOutbound`. |
| `ImEventEnricher.java` | Per-event enrichment (looks up sender's nickname/avatar for messages that arrive without them). |

### DTOs (1)

| File | One-line summary |
|---|---|
| `im/dto/ImRealtimeEvent.java` | Sealed-interface union of every event type the IM channel can carry — 19 records including `GroupMessage` (placeholder, not currently emitted). |

## Dependencies

- **Inbound**: subscribed to by the Angular frontend over `/ws/im`; consumed by `/api/im/messages/*`.
- **Outbound**: `auth.HelloTalkAuthClient` (status-105 relogin), `auth.HelloTalkAuthClient` (status-105 relogin), `core.AuthTokenHolder`, `core.UidExtractor`, `core.JilaliProperties`, `client` (via transport-typed REST round-trips for status-105 only — not for normal DM sends), `crypto` (for frame decryption).

## Comments and findings

- **STRUCTURALLY PARALLEL TO `realtime`** — see `packages/realtime.md` for the detailed comparison table. The biggest engineering problem in the codebase from a maintainability standpoint: this and `realtime` independently implement the same "maintain WS upstream, fan out, relay" pattern with subtle variations. A shared `UpstreamWebSocketConnector<TEvent>` base class could eliminate ~half the lines in both packages. The two packages are differentiated ONLY by (a) wire format (binary vs JSON) and (b) cardinality (singleton vs per-`cname`).
- **`AuthTokenHolder` integration verified**: `ImEventSource` reads the live upstream JWT from `AuthTokenHolder`, not `JilaliProperties.defaultAuthToken()` (recently refactored away).
- **`GroupMessage` is intentionally not emitted** — placeholder for the future group-chat 0x7049 wire protocol.
- **Auto-relogin recently added** in `HtImUpstreamConnector.attemptRelogin` — design should be re-evaluated (infinite-loop risk, structured concurrency fit).

## Improvement opportunities

1. **High**: extract a shared `UpstreamWebSocketConnector<TEvent>` base with `realtime.HtLiveHubUpstreamConnector`. Use Micronaut `@Retryable` for the reconnect logic instead of the manual `ExponentialBackoff` helper.
2. **High**: ensure the auto-relogin retry path is bounded (e.g. via `StructuredTaskScope.join()` cancel semantics, or an explicit retry-count limit) — current `attemptRelogin` on relogin failure just calls `close()` which is fine, but the relogin-then-reconnect failure path should similarly be bounded.
3. **Medium**: `ImSendController`'s REST endpoints and `ImSocketController`'s WS relay could share a single coherent inbound-side abstraction (currently two controllers coordinate via `ImEventSource` as the in-process pub-sub seam, which works but is implicit).
4. **Low**: `ImEventEnricher` enrichment is sequential; could be parallel via `StructuredTaskScope` if many enriched events arrive in burst — measure first before optimizing.
