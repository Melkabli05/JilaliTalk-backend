# HtTranslateClient

`src/main/java/com/jilali/translate/HtTranslateClient.java`

## Purpose
Implements the translator transport against `/translate/v2/ai_translator/translate` using the shared `jlhub` Micronaut HTTP client. It serializes the request, applies translator-specific headers, retrieves the response, and normalizes failures.

## Public API
- `HtTranslateClient(@Client("jlhub") HttpClient httpClient, ObjectMapper mapper)` — injects the configured upstream client and JSON serializer.
- `String postAiTranslate(AiTranslateUpstreamRequest body, TranslateUpstreamHeaders headers)` — serializes and POSTs the request, returning non-empty response bytes as UTF-8; throws `JilaliException` on serialization, HTTP, or empty-body failures.

## Coupling
Implements `TranslateClient` and consumes its request/header DTOs. It is tightly coupled to Micronaut’s imperative HTTP client, Jackson, the `jlhub` client configuration, and `JilaliException`.

## Notes
- This is the sole concrete adapter, not a second interface or duplicate of `TranslateClient`.
- `HtTranslateClient.java:59-71` converts to `BlockingHttpClient` and retrieves `byte[]`, buffering the complete SSE body. There is no stream backpressure or cancellation after dispatch, and large responses consume heap; Micronaut owns response-resource cleanup because no response/stream handle escapes.
- Lines 63-65 log the full upstream error body. If the translator echoes text, ciphertext, or credentials, those values may enter application logs.
