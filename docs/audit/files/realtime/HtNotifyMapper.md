# HtNotifyMapper

`src/main/java/com/jilali/realtime/HtNotifyMapper.java` — `@Singleton public class`.

## Purpose
Parses LiveHub plain-JSON room-channel frames into `RoomRealtimeEvent`s. Handles heartbeat detection, msg-id/ack extraction, and a large `notify_type` → typed-event switch, with shape-based routing for entities whose notify_type isn't yet confirmed.

## Public API
- `HtNotifyMapper(ObjectMapper om)`.
- `OptionalLong heartbeatSec(String)` — first `heartbeat_sec` push.
- `boolean isHeartbeatResponse(String)` — has `heartbeat_time`.
- `Optional<String> msgId(String)` — extract `msg_id` for acking.
- `Optional<RoomRealtimeEvent> map(String)` — main decode; drops frames failing `requiresUserId`; malformed/error → `Error`/`Raw`.
- Package-private entity mappers (`mapGiftWish`, `mapReward`, `mapRewardInfo`, `mapPurchaseVip`, `mapReceiveVipGifts`, `mapTreasureReward`, `mapFgUpgradeAward`) invoked by shape via `mapByEntityShape`.

## Coupling
Imports `RoomRealtimeEvent`, Jackson. Called by `HtLiveHubUpstreamConnector.handleFrame` (gets first dibs before the CC mapper). Exercised directly by `HtNotifyMapperTest`.

## Notes
Structural parallel to `com.jilali.im.HtImNotifyMapper` — the "decode upstream → typed sealed events" step. CC-channel sibling is `HtCcNotifyMapper`; room mapper has stronger shape constraints so it decodes first.
