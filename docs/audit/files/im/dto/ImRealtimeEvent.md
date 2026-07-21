## Purpose

The `ImRealtimeEvent` sealed interface is the union of every event type the IM WebSocket relay forwards from HelloTalk's binary `ht_im/sock` channel to the browser over `/ws/im`. Used both in the backend (as the canonical typed event passed through `ImSocketController.sendEvent`) and in the Angular frontend (as the discriminated `ImEvent` it consumes from WebSocket text frames).

## Public API

Sealed `interface ImRealtimeEvent` permitting 19 nested types — records for "things that have data," plus the few nominal kinds that don't.

Connection events:
- `record ConnectionState(String state)` — `"connecting" | "connected" | "reconnecting" | "disconnected"`.

Personal-DM messages (the six msg_type values the reference `scriptv2.js` handles too):
- `record TextMessage(String fromUserId, String fromNickname, String fromHeadUrl, String text, long ts, String msgId)`
- `record ImageMessage(String fromUserId, String fromNickname, String fromHeadUrl, String imageUrl, long ts, String msgId)`
- `record GiftMessage(...)` — carries `giftId` + `count`.
- `record IntroductionMessage(...)` — typed `IntroductionPayload`.
- `record VoiceRoomShared(...)` — `cname`, `roomName?`, `topicName?`, `ts`, etc.
- `record LiveRoomShared(...)` — same shape, with `activityName?` instead of `roomName?`.

Room/Friends events (notify_type push):
- `record ProfileVisit(String visitorUserId, String nickname, String headUrl)`
- `record StageInvite(String userId, String cname)` — notify_type 18.
- `record ModInvite(String userId, String cname)` — 48.
- `record ModAccepted(String userId)`, `ModRemoved(String userId)`, `ModUnmuted(String userId)` — 34/35/40.
- `record Follow(String userId, String nickname, String headUrl, int status)` — 53. status 1=followed you, 2=followed back.

Real-time control:
- `record TypingIndicator(String fromUserId, boolean isTyping)` — flag TYPING push.
- `record ReadReceipt(String msgId)` — inbound peer's read confirmation (cmd 16386 path uses a different mechanism).

Reserved:
- `record GroupMessage(String senderId, String senderName, String roomName, String text)` — INTENTIONALLY NOT EMITTED. Placeholder for the distinct group-chat 0x7049 wire protocol confirmed real in `re_output` but not yet implemented.

Health/control:
- `record MessageAck(String msgId, long sequence, int prefix)` — sent in response to outbound DM acknowledges.
- `record AccountStatus(String status)` — `"banned" | "session_mismatch"`.
- `record Error(String message)`.

## Dependencies

- Jackson polymorphic JSON deserialization via `@JsonTypeInfo(..., property = "type")` + `@JsonSubTypes` mapping every variant's wire `"type"` string to its class — the relay calls `om.writeValueAsString(event)` per outbound frame.
- Backend construction: `HtImNotifyMapper.map(...)` (the polymorphic mapper) is the sole producer; `ImSocketController` is the sole relay.
- Consumed by the Angular frontend (via its `im-events.ts` union); no in-process callers between construction and outbound serialization.

## Coupling and cohesion

Appropriately decoupled: a sealed union is the canonical Java 25 idiom for "one of N typed events" and exhaustive `switch` checks are verified by the compiler. Adding a variant forces updates at every `switch` call site, which is the goal.

## Code smells

None structural. Two practical concerns:
- **`prefix` on `MessageAck` is an `int`, not an enum** — the value 0 vs. non-zero is semantically meaningful (delivered vs. read receipt semantically) and the frontend currently just maps `prefix !== 0` to `delivered`. Consider a sealed-ish small enum or at least a typed alias for clarity.
- **`GroupMessage` placeholder is acknowledged dead code in comments** — fine as a documented reserve, but a `// @SuppressWarnings("unused")` would signal intent at the language level rather than in comments only.

## Technical debt

- Prefix-vs-success convention on `MessageAck` should become a named enum (`0 = SENT_ONLY`, `non-zero = DELIVERED`) once the wire convention stabilizes.

## Duplicate logic

None — every variant represents one wire-level event type; no field-level overlap with other types.

## Dead or unused code

`GroupMessage` is unreachable today; documented as a reserved placeholder for the future group-chat wire protocol. Removing it would force re-adding it when that protocol is eventually built.

## Java 25 modernization opportunities

- The sealed-interface + `@JsonTypeInfo` polymorphic-mapping pattern is already a Java-25-modern pattern. No change needed.
- Pattern matching for `switch` at consumer sites (Angular side) is desirable but Java-side has no consumer to upgrade.

## Micronaut built-in opportunities

- This sealed-union model plays well with Micronaut's Serialization/json-binding — no Micronaut-specific change needed.

## Refactoring recommendations

1. **Low**: lift `MessageAck.prefix` to a tiny enum (or `DeliveryStatus`) for readability.
2. **Medium** (future): when group-chat 0x7049 lands, `GroupMessage`'s `roomName` may need to be richer (e.g. `roomId` + `roomName` + `roomType`). Plan to supersede the placeholder rather than mutate it in place.
