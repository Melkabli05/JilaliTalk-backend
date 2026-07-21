# HtImPacketFramer

`src/main/java/com/jilali/im/HtImPacketFramer.java` — package-private final utility class (all-static).

## Purpose
Build and parse the 20-byte-header binary packets of HelloTalk's `ht_im/sock` protocol: login, heartbeat, ACK, read-receipt, typing, private-message, offline-sync packets; plus zlib deflate/inflate and header parsing.

## Responsibilities
- Own the protocol constants (packet types 0xF1/0xF2/0xF5, command IDs, `HEADER_LEN`).
- Provide packet builders (`buildPacket` overloads, `buildAck`, `buildHeartbeat`, `buildReadReceipt`, `buildTypingIndicator`, `buildPrivateMessagePacket`).
- Provide byte utilities (`deflate`, `inflate`, `copyPayload`, `parseHeader`, `nextSeq`).
- Define the `Header` record.

## Public API
Package-private class; static members:
- Constants: `HEADER_LEN=20`; `PKT_RESPONSE=0xF1`, `PKT_PUSH=0xF2`, `PKT_TYPING=0xF5`; `CMD_LOGIN`, `CMD_HEARTBEAT`, `CMD_PONG`, `CMD_MSG_ACK`, `CMD_TYPING`, `CMD_PRIVATE_MSG`, `CMD_READ_RECEIPT`, `CMD_GROUP_MESSAGE`, `CMD_OFFLINE_SYNC`, `CMD_OFFLINE_SYNC_PAGE`, `CMD_OFFLINE_RESPONSE`, `CMD_GROUP_RESPONSE`.
- `int nextSeq()` — wraps an `AtomicInteger` masked to 16 bits.
- `byte[] buildPacket(long userId, int commandId, byte[] payload)` / `(…, int flag, …)` / `(long userId, long toId, int commandId, int flag, byte[] payload)`.
- `byte[] buildAck(byte[] inbound)`.
- `byte[] buildHeartbeat(long userId)`.
- `byte[] buildReadReceipt(long fromId, long toId, String msgId, int chatType)`.
- `byte[] buildTypingIndicator(long fromId, long toId, boolean isTyping)`.
- `byte[] buildPrivateMessagePacket(long fromId, long toId, byte[] jsonBody, boolean compress)`.
- `byte[] deflate(byte[])`, `byte[] inflate(byte[])`, `byte[] copyPayload(byte[] data, int payloadLen)`.
- `Header parseHeader(byte[] data)`.
- `record Header(int packetType, int keyType, int cmdId, int seqNum, long fromId, long toId, int payloadLen)`.
- Private ctor; private static `SEQ` (`AtomicInteger`).

## Dependencies
- Imports only JDK (`ByteBuffer`, `Inflater`, `DeflaterOutputStream`, `AtomicInteger`).
- Depended on by: `HtImFrameDecoder` (`copyPayload`, `inflate`, `HEADER_LEN`, `PKT_PUSH`, `Header`), `HtImUpstreamConnector` (builders + `parseHeader` via static import), `HtImNotifyMapper` (`Header` type in `map`), `ImSendController` (`buildReadReceipt`, `buildTypingIndicator`, `buildPrivateMessagePacket`). Grep-verified.

## Coupling and cohesion analysis
High cohesion — one wire format, one class. `Header` is the shared value carried across decoder/mapper/connector, making this the protocol's schema hub (afferent coupling from 4 classes). Static-only design is reasonable for a stateless codec but makes the shared mutable `SEQ` counter a process-global (see below). No networking or JSON-mapping concerns leak in.

## Code smells
- **Utility/God-of-constants concentration**: the class is both the constant registry and the builder factory. Acceptable but broad.
- **Shared mutable static** `SEQ` (line 36): a single process-wide sequence counter across all connections. For the single-user IM channel this is fine, but it is global state in a "pure helper."
- **Boolean parameter** `compress` on `buildPrivateMessagePacket` and `isTyping`/`nowrap` flags — minor flag-argument smell.
- **Magic offsets** in `buildAck` (`in.getShort(4)`, `ack.put(inbound, 8, 8)`) mirror `parseHeader` offsets without sharing them.

## Technical debt
- `CMD_GROUP_MESSAGE` (0x7049) is declared but never sent — reserved for the not-yet-implemented group path (matches the `ImRealtimeEvent.GroupMessage` placeholder). Note as **reserved for future feature**, not dead.
- `buildReadReceipt`'s `chatType` single-byte encoding was **recently fixed** (was a 4-byte int). The extensive smali-citation javadoc is evidence of prior protocol misreads and thin regression testing.
- `inflate`'s "try nowrap false then true, else null" is a brittle heuristic carried from reverse engineering.

## Duplicate logic
No realtime counterpart — LiveHub is text/JSON and builds frames via `om.createObjectNode()` inside `HtLiveHubUpstreamConnector` (`initFrame`/`heartbeatFrame`/`ackFrame`). The two channels' frame construction is fundamentally different (binary packing vs JSON). This is the strongest evidence that a *shared connector base* can only cover lifecycle (connect/backoff/heartbeat), not framing. See `docs/audit/packages/im.md`.

## Dead or unused code
- `CMD_GROUP_MESSAGE` unused (reserved, documented — not flagged dead).
- All builders reachable from connector/controller. `buildPacket(long,int,byte[])` two-arg form used by `buildHeartbeat` and login/offline paths. Grep-verified no orphan methods.

## Java 25 modernization opportunities
- Group the command IDs into an `enum Command { LOGIN(0x1025), … }` with an `int cmdId()`; the connector's `handlePacket` if-chain (`if h.cmdId()==CMD_MSG_ACK …`) could then become a `switch` on the enum. Modest gain given `int` comparisons are fine, but improves readability and exhaustiveness.
- `Header` is already a record; consider adding derived accessors (e.g. `boolean isPush()`).

## Micronaut built-in opportunities
None — pure codec, correctly not a bean. The `SEQ` global could be replaced by a per-connection sequence if the connector were a prototype bean, but Micronaut adds nothing to byte packing.

## Refactoring recommendations
1. Promote command IDs to an `enum` and thread it into the connector dispatch.
2. Move `SEQ` ownership into the connector (per-connection sequence) to avoid process-global state and make the framer fully pure/static-safe.
3. Share header field offsets between `parseHeader` and `buildAck` via named constants.
4. Replace boolean flags with intent-named overloads where hot (`buildPrivateMessagePacketCompressed`).
