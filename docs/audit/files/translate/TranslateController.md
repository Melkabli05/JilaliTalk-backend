# TranslateController

`src/main/java/com/jilali/translate/TranslateController.java`

## Purpose
Exposes the BFF’s synchronous `POST /api/translate` endpoint. It validates the inbound JSON request, delegates translation to `TranslateService`, and wraps the resulting text in the public response shape.

## Public API
- `TranslateController(TranslateService service)` — constructor-injects the translation orchestrator.
- `TranslateResponse translate(TranslateRequest request)` — translates the request text into `targetLang`; Micronaut binds the body and applies Jakarta validation before invocation.

## Coupling
Depends directly on `TranslateService`, `TranslateRequest`, and `TranslateResponse`, plus Micronaut HTTP, validation, and executor annotations. No upstream protocol or cryptography leaks into this boundary.

## Notes
- `@ExecuteOn(TaskExecutors.BLOCKING)` at `TranslateController.java:15` is necessary because the current client uses `BlockingHttpClient`; it also means each request occupies a blocking-pool thread until the entire upstream SSE response is received.
- `@NotBlank` validation comes from `TranslateRequest`, but there is no text-length or target-language format limit. Very large accepted input increases encryption, upstream, buffering, and cache costs.
- Despite the upstream SSE protocol, this endpoint returns one ordinary JSON response after all chunks are joined; it does not stream to the frontend.
