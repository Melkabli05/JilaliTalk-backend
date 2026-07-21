# HtCcNotifyMapper

`src/main/java/com/jilali/realtime/HtCcNotifyMapper.java` — `@Singleton public class`.

## Purpose
Parses LiveHub frames belonging to the AI-captioning / subtitle channel (`LiveCCNotify` on Android) into `RoomCcRealtimeEvent`s. Shares one WebSocket with the room channel; discriminates CC frames by `notify_info` shape rather than `notify_type` (the two channels reuse the same integer namespace).

## Public API
- `HtCcNotifyMapper(ObjectMapper om)` — constructor.
- `Optional<RoomCcRealtimeEvent> map(String text)` — decode a CC frame; re-runs the discriminator standalone-safe. Malformed/failed → `Error`.
- `boolean ownsType(String text)` — cheap pre-check: `notify_type ∈ CC_TYPES` AND `notify_info` has a CC marker (or bare-cname lifecycle type 2/6/kill-set) AND no room marker.
- Private `mapEvent` switch → SubtitleStart(1)/End(2)/Disabled(3)/Line(4)/ExperienceActivated(6)/Expired(12); kill-set 5/7/9/10/11 → `Raw`.
- Constants `CC_TYPES`, `CC_KILL_TYPES`, `ROOM_MARKERS`, `CC_MARKERS`.

## Coupling
Imports `RoomCcRealtimeEvent`, Jackson. Called by `HtLiveHubUpstreamConnector.handleFrame` (gated by `ownsType`).

## Notes
Structural parallel to `com.jilali.im.HtImNotifyMapper` — both are the "decode upstream frames → typed sealed events" step. Room-channel sibling is `HtNotifyMapper`; the two split one socket by frame shape.
