# CaptionHistoryResponse.java

`src/main/java/com/jilali/comment/dto/CaptionHistoryResponse.java`

## Purpose
Wrapper for the upstream caption-history response on `GET /api/captions/history`. Carries the page list and a `has_more` flag.

## Public API
Record `CaptionHistoryResponse(@Nullable List<CaptionEntry> list, @JsonProperty("has_more") boolean hasMore)`:
- `list` — nullable upstream page; `list()` accessor normalizes null to `List.of()`.
- `hasMore` — upstream `has_more` flag.

## Coupling
- Imported by: `CommentController.history` (pass-through return) and `JilaliClient` (upstream deserialization).
- Imports: `com.fasterxml.jackson.annotation.JsonProperty`, `io.micronaut.core.annotation.Nullable`, `io.micronaut.serde.annotation.Serdeable`, `java.util.List`.

## Notes
- The `list()` accessor overriding the canonical accessor to coalesce null is a defensive idiom repeated across `CommentListResponse` and `CommentListDto` — minor duplicate pattern.
- `hasMore` / `has_next` naming diverges from `CommentListResponse.has_next` — same concept, different field name, no shared abstraction.
