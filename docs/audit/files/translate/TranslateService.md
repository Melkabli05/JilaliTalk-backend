# TranslateService

`src/main/java/com/jilali/translate/TranslateService.java`

## Purpose
Orchestrates translation: generate an ephemeral X25519 session, encrypt the text field, construct translator headers, invoke the upstream client, then parse, decrypt, and concatenate SSE chunks. Results are cached by text and target language.

## Public API
- `TranslateService(TranslateClient, EncryptedFieldCodec, JilaliProperties, AuthTokenHolder, ObjectMapper)` — injects transport, codec, configuration, authentication, and JSON parsing collaborators.
- `String translate(String text, String targetLang)` — performs or retrieves a cached translation and throws `JilaliException` when no usable result is produced.

## Coupling
Coordinates auth/config utilities, `Curve25519SessionGenerator`, translate DTOs/codec, Jackson, caching, and the `TranslateClient` port.

## Notes
- This is not live SSE processing: `TranslateService.java:93-94` receives one complete `String`, and lines 110-115 iterate it afterward. Consequently there is no chunk-level backpressure, cancellation, or early delivery; response size is bounded only by HTTP-client/server limits.
- The service opens no stream itself, so HTTP response cleanup belongs to `HtTranslateClient`/Micronaut. Malformed JSON events are logged and skipped, but Base64/decryption failures at line 115 abort the whole translation.
- Exact `data: ` and `[DONE]` matching makes the parser intentionally narrow.
