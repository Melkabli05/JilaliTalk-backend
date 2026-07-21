# SseChunk

`src/main/java/com/jilali/translate/dto/SseChunk.java`

## Purpose
Represents one JSON object carried by an upstream SSE `data:` line. The nested `result` value is one Base64-encoded encrypted fragment that must be decrypted and concatenated in arrival order.

## Public API
Canonical record constructors/accessors for outer fields:
- `code: int` — envelope status code.
- `message: String` — envelope status message.
- `data: SseChunk.ChunkData` — nested chunk payload.

`ChunkData` exposes:
- `id: String` — chunk/request identifier.
- `object: String` — upstream object type.
- `model: String` — producing model.
- `createdAt: long` — upstream creation value, read from `created_at`.
- `result: String` — Base64 ciphertext for this fragment.

## Coupling
Jackson/Micronaut Serde deserializes it in `TranslateService`, which passes `data.result` to `EncryptedFieldCodec`.

## Notes
- Both records ignore unknown JSON properties, which permits additive upstream changes.
- Missing primitives become `0`; references can be null without `@Nullable`. `TranslateService.java:113-115` guards `data`/`result` but ignores `code`/`message`, recognizing error envelopes only when no result remains.
- `[DONE]` is not represented by this DTO; the service filters that sentinel before parsing.
