# `com.jilali.realtime` + `realtime.dto` — voice-room (LiveHub) realtime channel

## Purpose

Maintains one upstream WebSocket per active voice room (LiveHub), decodes pushed notify/CC (live-caption) frames, and relays them to the browser over `/ws/ht/{cname}`. Structurally parallel to `com.jilali.im` (personal 1:1 DM channel).

## File responsibilities (5 root + 2 dto = 7 files)

### Root

| File | One-line summary |
|---|---|
| `HtLiveHubUpstreamConnector.java` | Per-room binary WebSocket lifecycle: connect (with reconnect/backoff/heartbeat), decode JSON frames, dispatch to `RoomRealtimeEvent` or `RoomCcRealtimeEvent`. |
| `HtNotifyMapper.java` | Decodes a LiveHub notify push into typed `RoomRealtimeEvent`. |
| `HtCcNotifyMapper.java` | Same shape but for captioning (CC) updates → `RoomCcRealtimeEvent`. |
| `RoomEventSource.java` | Pub-sub fan-out: first subscriber per `cname` opens the upstream, last leaves closes it. Reads the live token from `AuthTokenHolder` (verified). |
| `RoomSocketController.java` | Browser-facing WebSocket endpoint relaying serialized events to subscribed tabs. |

### DTOs (2)

- `RoomRealtimeEvent` (sealed): the 1:1 union of every event type LiveHub can push (user-join/leave/quit, stage-join/leave, gift, mod-promote/demote, mic-on/off, etc.).
- `RoomCcRealtimeEvent` (sealed): captioning-specific events (caption-on/off, caption-language-change, caption-update).

## Dependencies

- **Inbound**: subscribed-to by `RoomEventSource` browsers and by anything in the backend that wants realtime room state.
- **Outbound**: `core` only — no feature-package dependencies at the source/connector layer.

## Structural duplication with `im` (confirmed by audit agent)

| Concern | `com.jilali.realtime` | `com.jilali.im` |
|---|---|---|
| Per-context WS connector | `HtLiveHubUpstreamConnector` (per `cname`) | `HtImUpstreamConnector` (per BFF process) |
| Decoded sealed-event union | `RoomRealtimeEvent`, `RoomCcRealtimeEvent` | `ImRealtimeEvent` |
| Notify mapper | `HtNotifyMapper`, `HtCcNotifyMapper` | `HtImNotifyMapper` |
| Pub-sub event source | `RoomEventSource` (multi-`cname`) | `ImEventSource` (single) |
| Browser-facing WS relay | `RoomSocketController` (push-only) | `ImSocketController` (also push; separate `ImSendController` for outbound) |

Difference: `realtime` is plain JSON (no binary frame decoder/encoder), `im` is binary-framed. `realtime` is multi-context (one per `cname`), `im` is singleton. `realtime` has no outbound send, `im` does.

## Concrete duplication estimate (per audit agent)

Most of these are parallel implementations of the same pattern (connect-WS-upstream + decode + fan-out + relay). A shared `UpstreamWebSocketConnector<TEvent>` base class could likely eliminate on the order of half of these files (~300-500 lines). Micronaut's built-in `@Retryable` would also replace the manual reconnect loop.

## Improvement opportunities

1. **High**: extract a shared `UpstreamWebSocketConnector<TEvent>` abstract base covering reconnect+heartbeat+decode+dispatch (using Micronaut `@Retryable` instead of the manual `ExponentialBackoff` helper).
2. **High**: `RoomSocketController` is push-only — consider using Micronaut's reactive-WS support instead of a manual `@ServerWebSocket`.
3. **Medium**: `RoomCcRealtimeEvent` events could plausibly be a sub-type of `RoomRealtimeEvent` (or share the union) rather than two separate event hierarchies; verify whether CC has its own browser subscription or piggybacks on the room WS.
4. **Low**: consolidate the dispatch loop (`HtLiveHubUpstreamConnector`'s onMessage-style switch) with `HtImUpstreamConnector`'s once the shared base lands.
