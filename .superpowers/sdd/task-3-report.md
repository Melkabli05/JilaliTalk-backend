# Task 3: HtImFrameDecoder — Report

## What was done

Created `HtImFrameDecoder` — a pure, stateless class handling all byte-level decode logic from `HtImUpstreamConnector`. No networking, no mutable state.

### Files created

- `src/main/java/com/jilali/im/HtImFrameDecoder.java` — the decoder class
- `src/test/java/com/jilali/im/HtImFrameDecoderTest.java` — 10 unit tests

### Implementation

`HtImFrameDecoder` exposes:

- `decodeF1(byte[], int) -> Optional<JsonNode>` — decompresses and parses F1 (login response/pong) frames
- `decodeF2(byte[], int, byte[]) -> F2Push` — decrypts QQTEA, then routes to Receipt / Poke / Json / Unknown
- `decodeTypingStatus(byte[], int, byte[], int) -> boolean` — reads the typing bit from a payload
- `decodeOfflinePacket(String, byte[]) -> Optional<OfflinePacket>` — base64-decodes, decrypts, and parses an offline packet

`F2Push` is a sealed interface with 6 cases: `Receipt`, `Poke`, `Json`, `Unknown`, `DecryptFailed`, `Ignored`.

Uses shared utilities from Task 2: `HtImPacketFramer.inflate(byte[])`, `HtImPacketFramer.copyPayload(byte[], int)`, `HtImPacketFramer.Header`.

### Test results

All 10 new tests pass. Full suite (`./gradlew test`): BUILD SUCCESSFUL.

## Commit

`ed6a2a6` — feat(im): add HtImFrameDecoder, pure byte-level decoding for ht_im/sock frames
