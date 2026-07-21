# `com.jilali.realtime.dto` — LiveHub event unions

## Files (2)

| DTO | Purpose |
|---|---|
| `RoomRealtimeEvent` | Sealed-interface union of every event type LiveHub can push to a subscribed voice room. Per-room canonical-typed-event seam — same role as `ImRealtimeEvent` plays for the personal IM channel. Includes entry/exit, stage actions, gift, mod-promote/demote, mic-on/off, etc. |
| `RoomCcRealtimeEvent` | Sealed-interface union of captioning-specific events (caption on/off, language change, caption update). **Could plausibly be a sub-type of `RoomRealtimeEvent`** rather than a sibling hierarchy — verify whether CC pushes arrive on the same browser WS as the room WS or on a separate one before deciding. |

## Dependencies

- Imported by `RoomEventSource` (brokers outbound relay), `RoomSocketController` (browser serializer), `HtNotifyMapper` & `HtCcNotifyMapper` (producers).

## Improvement opportunities

1. **Medium**: consolidate or unify `RoomCcRealtimeEvent` with `RoomRealtimeEvent` once the WS-channel-sharing question is answered.
2. **Low**: ensure both interfaces follow the same naming conventions for shared common fields (already consistent per audit).
