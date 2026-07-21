# HtImFrameDecoder

`src/main/java/com/jilali/im/HtImFrameDecoder.java` — package-private final class.

## Purpose
Pure byte-level decoding for the HelloTalk `ht_im/sock` binary channel: QQTEA decrypt, zlib inflate/gunzip, and JSON extraction. Converts raw packet payloads into structured `F2Push` / `OfflinePacket` / `MessageAckView` values. No networking, no mutable state beyond a per-instance `ObjectMapper`.

## Responsibilities
- Decode F1 (login/response) payloads to `JsonNode` (`decodeF1`).
- Decode F2 (encrypted push) payloads: decrypt with session key, branch on first-byte magic (0x25 receipt, 0x08 poke, 0x78 zlib, 0x7B raw-JSON), produce a `F2Push` variant.
- Decode typing-status bodies (`decodeTypingStatus`).
- Decode base64-wrapped offline-sync packets, including keyType-0/1 heuristic decrypt and gzip detection (`decodeOfflinePacket`).
- Parse the cmdId-16386 MSG-ACK binary body into `MessageAckView` (`decodeMsgAck`) with a strict-layout path plus a UUID-regex fallback.

## Public API
Class is package-private; members are package-visible.
- `HtImFrameDecoder(ObjectMapper om)` — constructor.
- Nested sealed interface `F2Push` with variants: `Receipt(String msgId)`, `Poke()`, `Json(JsonNode root)`, `Unknown(int firstByte, byte[] bytes)`, `DecryptFailed()`, `Ignored()`.
- `record OfflinePacket(Header header, F2Push body)`.
- `record MessageAckView(String msgId, long sequence, int prefix)`.
- `Optional<JsonNode> decodeF1(byte[] data, int payloadLen)`.
- `F2Push decodeF2(byte[] data, int payloadLen, byte[] sessionKey)`.
- `boolean decodeTypingStatus(byte[] data, int payloadLen, byte[] sessionKey, int keyType)`.
- `Optional<OfflinePacket> decodeOfflinePacket(String base64, byte[] sessionKey)`.
- `Optional<MessageAckView> decodeMsgAck(byte[] payload)`.
- Private helpers: `decodePushBody`, `stripNulls`, `isRecognizedMagic`, `gunzip`; static field `UUID_PATTERN`.

## Dependencies
- Imports: Jackson `JsonNode`/`ObjectMapper`, `com.jilali.crypto.QqTeaCipher`, `HtImPacketFramer.Header`, static `HEADER_LEN`/`PKT_PUSH`, JDK `ByteBuffer`/`Base64`/`GZIPInputStream`/`Inflater` (via `HtImPacketFramer.inflate`).
- Delegates byte plumbing (`copyPayload`, `inflate`) to `HtImPacketFramer`.
- Depended on by: `HtImUpstreamConnector` (sole runtime caller), `HtImFrameDecoderTest`. Referenced in javadoc of `ImRealtimeEvent.MessageAck`.

## Coupling and cohesion analysis
High cohesion: every method is a byte→structured-value function keyed on the same wire protocol. Efferent coupling is low (only `QqTeaCipher`, `HtImPacketFramer`, Jackson). Tight semantic coupling to the wire-format constants in `HtImPacketFramer` and to `QqTeaCipher`'s contract, but that is inherent to a decoder. Good separation from networking (`HtImUpstreamConnector`) and mapping (`HtImNotifyMapper`).

## Code smells
- **Long Method / Primitive Obsession**: `decodeMsgAck` (lines 187-257) is ~70 lines with two nested strategies, manual BE/LE `ByteBuffer` juggling, magic offsets (`2 + strLen`, `raw.length - 8`), and a swallowed-exception fall-through between paths. Hard to follow.
- **Primitive Obsession**: pervasive raw `byte[]` + `int payloadLen` pairs threaded through every method rather than a small `Frame`/`Payload` value type.
- **Magic numbers**: first-byte constants (0x25, 0x08, 0x78, 0x7B, 0x1F/0x8B), length thresholds (`< 38`, `<= 16`, `>= 6`) are inline literals; only some are named.
- **Duplicated inflate-if-0x78 idiom** appears in `decodeF2`/`decodeTypingStatus`/`decodeMsgAck`/`decodeOfflinePacket`.
- Empty catch with `_` unnamed variable used consistently (Java 22+ unnamed patterns) — acceptable but hides genuine decode faults from logs.

## Technical debt
- The dual BE/LE guessing in `decodeMsgAck` and the "try both nowrap modes" inflate reflect reverse-engineered ambiguity in the wire format — legitimately hard to remove but under-tested for edge lengths.
- No logging on decode failure (returns `Optional.empty()`), so silent drops are invisible in production.
- `decodePushBody` is shared by live and offline paths; a change to magic-byte handling is a single point but easy to break both silently.

## Duplicate logic
Within the IM package this is the byte-decode layer; there is no direct realtime counterpart because **LiveHub frames are plain-text JSON** (`HtNotifyMapper.readTreeOrNull` is a one-line `om.readTree`). This asymmetry is the core reason the two upstream channels cannot fully share a decode stage: `im` is binary/encrypted/zlib, `realtime` is text/JSON. See `docs/audit/packages/im.md` structural-duplication section. No duplication to eliminate here.

## Dead or unused code
None. All methods reachable from `HtImUpstreamConnector` or the test. `F2Push.Unknown.bytes()`/`firstByte()` accessors used in logging (`HtImUpstreamConnector.dispatchPush`). Grep-verified: only referenced by connector + test.

## Java 25 modernization opportunities
- `decodePushBody` (lines 74-101) is an int-magic-byte dispatch that would read cleanly as a `switch` on `firstByte` — not sealed, but a labeled `switch` expression removes the if-chain.
- `decodeMsgAck` could use **record patterns** if the parsed layout were modeled as records; more realistically, extract a `ParsedAckBody` record and pattern-match.
- The `F2Push` sealed interface is already idiomatic Java 25; consumers in the connector already use switch-on-sealed. No change needed here, but it is the template the rest of the batch should follow.
- Unnamed variables in catch (`catch (Exception _)`) already Java 22+; fine.

## Micronaut built-in opportunities
None directly — this is pure computation with no framework surface. It is intentionally not a bean (constructed by hand in the connector), which is correct for a stateless helper. Could optionally be a `@Singleton` sharing the injected `ObjectMapper` if the connector became a bean.

## Refactoring recommendations
1. Extract a small `WireBytes` value type (data + offset + len) to kill the `byte[] data, int payloadLen` primitive-pair threading.
2. Split `decodeMsgAck` into `tryStrictAckLayout` + `tryUuidRegexAck` returning `Optional<MessageAckView>`; name the offset math.
3. Centralize the "inflate if leading 0x78" idiom into one helper (partly already `HtImPacketFramer.inflate`, but callers re-check the magic byte).
4. Add debug logging at each `Optional.empty()`/`Ignored` return so silent drops are diagnosable.
