# TranslateResponse

`src/main/java/com/jilali/translate/dto/TranslateResponse.java`

## Purpose
Defines the public JSON response returned by `POST /api/translate`. It exposes the fully assembled plaintext translation while hiding upstream SSE, encryption, model, and status details.

## Public API
Canonical record constructor and accessor for:
- `translatedText: String` — complete decrypted translation returned to the caller.

As a Java record it also supplies generated `equals`, `hashCode`, and `toString`; `@Serdeable` enables Micronaut serialization.

## Coupling
Constructed only by `TranslateController` from `TranslateService.translate` and serialized by Micronaut. It has no dependency on the upstream request/chunk DTOs or crypto layer.

## Notes
- There is no explicit nullability or validation annotation. In the current path, `TranslateService` either returns a non-empty joined string or throws, so the controller does not normally construct a null/empty response.
- The wire shape is a single `{ "translatedText": ... }` property. It intentionally omits source/target language, model, cache status, and partial-chunk metadata, so clients cannot inspect those details without a contract change.
- Generated `toString()` includes plaintext translated content; avoid logging the record wholesale where user text may be sensitive.
