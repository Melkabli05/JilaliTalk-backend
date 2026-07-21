# `com.jilali.translate.dto` — translation request/response shapes

## Files (5)

| DTO | Purpose |
|---|---|
| `AiTranslateUpstreamRequest` | BFF→translation-microservice request body, built by `TranslateService` and encrypted via `EncryptedFieldCodec`. |
| `SseChunk` | Server-sent-event chunk shape (`data:`/`[DONE]` parsing). SSE is **fully buffered** into a `byte[]`/`String` before parsing — no incremental delivery, no backpressure, no cancellation. |
| `TranslateRequest` | Frontend-facing inbound request body. |
| `TranslateResponse` | Frontend-facing outbound response (translated text). |
| `TranslateUpstreamHeaders` | Per-request upstream headers (cached-by-uid for cache-key consistency, per `TranslateService`'s doc comment). |

## Dependencies

- Used by `TranslateController`, `TranslateService`, and by `client.JilaliClient`'s `translateSend` method (declared in `TranslateClient` interface, implemented in `HtTranslateClient`).

## ⚠ Findings (carried forward)

- **No backpressure**: SSE is buffered fully into memory before parsing — under load, this is a memory and latency problem, not just a theoretical concern. A target rewrite should stream the SSE through a Micronaut reactive consumer.
- **Cache key includes uid** but `TranslateService`'s doc explicitly explains it does so for `Frontend identity = upstream JWT subject identity` consistency — this is correct given the single-account BFF design but worth re-validating if the BFF ever becomes multi-user.

## Improvement opportunities

1. **Medium**: convert `SseChunk` parsing from buffered to streaming (Micronaut reactive).
2. **Low**: clear documentation of the cache-key contract (already in `TranslateService`'s Javadoc — keep it during the rewrite).
