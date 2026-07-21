# `com.jilali.translate` + `translate.dto` + `translate/codec` — AI translation

## Purpose

AI message translation via a separate encrypted microservice (distinct from HelloTalk's main API). Uses Curve25519 ephemeral keypairs + AES for the encrypted field transport. Streams responses back to the Angular frontend as SSE.

## File responsibilities (4 root + 1 codec + 5 dto = 10 files)

### Root

| File | One-line summary |
|---|---|
| `TranslateController.java` | `/api/translate/*` REST endpoint. |
| `TranslateService.java` | Orchestrates a single translation request: ephemeral keypair → encrypts the user text → POSTs to the translation microservice → parses the encrypted-field response. Uses Micronaut `@Cacheable("ai-translate")` for repeated lookups. |
| `TranslateClient.java` | The **port interface** — a small declarative `@Client` shape that services depend on, kept as a substitution seam. |
| `HtTranslateClient.java` | **Sole concrete adapter** — `HtTranslateClient implements TranslateClient`, injecting `@Client("jlhub") HttpClient`. Port-and-adapter design — NOT a duplicate of `TranslateClient`. |

### Codec (1)

| File | One-line summary |
|---|---|
| `EncryptedFieldCodec.java` | Curve25519 ephemeral-session keypair + AES-encrypts the request text and decrypts the response. Holds shared secrets — verify thread-safety and SecureRandom usage. |

### DTOs (5)

| DTO | Purpose |
|---|---|
| `AiTranslateUpstreamRequest.java` | Request payload built and encrypted by `TranslateService`. |
| `SseChunk.java` | Server-sent-event chunk shape — `data:`/`[DONE]` parsing. |
| `TranslateRequest.java` | Inbound REST request from the Angular frontend. |
| `TranslateResponse.java` | Outbound REST response. |
| `TranslateUpstreamHeaders.java` | Per-request upstream headers. |

## Dependencies

- **Inbound**: Angular frontend consumes the REST endpoint.
- **Outbound**: `core` (config + auth token holder, for the per-request JWT), `crypto` (Curve25519 primitives).

## Comments from the audit

- `TranslateClient` (port) and `HtTranslateClient` (adapter) follow a correct port-and-adapter pattern — verified by the per-file doc.
- `EncryptedFieldCodec` is a security-sensitive component (handles ephemeral keypairs and AES). The audit agent flagged AES-ECB mode, missing input/key validation, and the configurable hardcoded default public key in `application.yml` for review.
- SSE streaming via `SseChunk` is fully buffered (`byte[]`/`String` before parsing) — no incremental delivery, no backpressure, no cancellation. Under load, memory/latency concern.

## Improvement opportunities

1. **Medium**: convert manual SSE parsing to a Micronaut reactive-streaming consumer so we get backpressure for free.
2. **Medium**: tighten `EncryptedFieldCodec` (input validation, non-ECB mode if the encryption strength allows it without breaking wire compatibility).
3. **Low**: replace `EncryptedFieldCodec`'s ad-hoc field encoding with sealed `TranslationRequestBody`/`TranslationResponseBody` records, lifted from the codec into a domain package — improves testability and keeps the wire codec decoupled from data classes.
4. **Low**: keep `TranslateClient` (port) and `HtTranslateClient` (adapter) — DO NOT collapse, the port-and-adapter seam is correct.
