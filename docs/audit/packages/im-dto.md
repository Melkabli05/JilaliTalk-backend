# `com.jilali.im.dto` — IM-channel event union

## Files (1)

| DTO | Purpose |
|---|---|
| `ImRealtimeEvent` | Sealed-interface union of every event type the personal IM channel carries — 19 records including a documented `GroupMessage` placeholder for the un-implemented 0x7049 group-chat path. The full per-variant enumeration is in the per-file doc; the package-level intersection note is that **this is THE canonical typed-event seam** between the IM connector (`HtImUpstreamConnector` + `HtImNotifyMapper`) and the browser relay (`ImSocketController`) — and analogously the only contract value that crosses the personal-IM boundary. |

## Dependencies

- Imported by `HtImUpstreamConnector` (mapper returns these), `ImSocketController` (serializes via `om.writeValueAsString` per outbound frame), and the Angular frontend's `im-events.ts` discriminated union (consumer-side, in another repo).

## Improvement opportunities

1. **Medium**: `MessageAck.prefix` is currently `int` (0 = SENT_ONLY, non-zero = DELIVERED per the audit). Promote to a tiny enum or sealed type for readability once the convention is stable.
2. **Low**: when group-chat 0x7049 lands, plan to supersede the `GroupMessage` placeholder rather than mutate in-place — it currently lacks the `roomId`/`sender_id` fields that the real wire protocol requires (smali: `zy/a.smali:383-424`).
