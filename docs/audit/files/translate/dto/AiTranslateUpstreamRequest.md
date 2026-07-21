# AiTranslateUpstreamRequest

`src/main/java/com/jilali/translate/dto/AiTranslateUpstreamRequest.java`

## Purpose
Models the JSON body sent to the upstream AI translator. Only `text` contains Base64-encoded ciphertext; routing, model, and language options remain plaintext.

## Public API
Canonical record constructor and accessors for:
- `preferredTargetLang: String` — requested target language, serialized as `preferred_target_lang`.
- `context: String` — optional translator context (`@Nullable`).
- `text: String` — encrypted, Base64-encoded source text.
- `appId: String` — upstream application identifier, serialized as `app_id`.
- `model: String` — translator model identifier.
- `transliterate: boolean` — whether transliteration is requested.
- `fallbackTargetLang: String` — optional fallback language, serialized as `fallback_target_lang`.

## Coupling
Constructed by `TranslateService`, serialized by `HtTranslateClient`, and coupled to Jackson property names plus Micronaut Serde/nullability metadata.

## Notes
- There are no validation annotations because this is an internal upstream DTO; invalid/null required values are not rejected at construction.
- `TranslateService.java:39-40` hardcodes `appId` as `HelloTalk` and `model` as `qwen_mt_plus`, while lines 84-90 currently always use null context/fallback and disable transliteration. The record supports more upstream options than the current service exposes.
