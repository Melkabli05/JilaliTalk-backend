# TranslateClient

`src/main/java/com/jilali/translate/TranslateClient.java`

## Purpose
Defines the transport port between `TranslateService` and the translator upstream. The service supplies a prepared request/header pair and receives the upstream SSE payload without depending on Micronaut HTTP types.

## Public API
- `String postAiTranslate(AiTranslateUpstreamRequest body, TranslateUpstreamHeaders headers)` — posts one AI-translation request and returns the raw `text/event-stream` body as a single UTF-8 string; implementations map transport failures to `JilaliException`.

## Coupling
Uses only the two translate wire DTOs. Grep confirms `TranslateService` is its sole production consumer and `HtTranslateClient` its sole implementation.

## Notes
- `TranslateClient` and `HtTranslateClient` are not duplicate client interfaces: this file is the interface/port, while `HtTranslateClient implements TranslateClient` as the concrete Micronaut adapter. Neither is dead or redundant under the current port-and-adapter design; merging them would remove the substitution/test seam rather than eliminate duplicate behavior.
- Returning `String` bakes full-response buffering into the abstraction. Even a future reactive HTTP implementation could not expose SSE backpressure, cancellation, or incremental chunk processing without changing this method’s return type.
