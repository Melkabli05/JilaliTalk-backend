# TranslateRequest

`src/main/java/com/jilali/translate/dto/TranslateRequest.java`

## Purpose
Defines the public JSON request accepted by `POST /api/translate`. It carries the source text and the upstream target-language identifier.

## Public API
Canonical record constructor and accessors for:
- `text: String` — source text to translate; `@NotBlank` rejects null, empty, and whitespace-only values during controller validation.
- `targetLang: String` — requested target language; also `@NotBlank`.

As a Java record it also provides generated `equals`, `hashCode`, and `toString` implementations.

## Coupling
Deserialized by Micronaut Serde and validated through Jakarta Validation in `TranslateController`. Its two values are passed directly to `TranslateService.translate`.

## Notes
- Validation establishes presence only. There is no maximum text length, supported-language enum/pattern, normalization, or canonical case policy, so malformed language codes reach the upstream and equivalent spellings can occupy distinct cache keys.
- Record `toString()` includes the complete source text. The current production code does not log the request object, but future diagnostic logging should avoid doing so if translated text may be sensitive.
- The DTO deliberately contains no user/session identity; authentication is resolved separately by the service.
