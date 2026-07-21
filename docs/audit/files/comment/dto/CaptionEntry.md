# CaptionEntry.java

`src/main/java/com/jilali/comment/dto/CaptionEntry.java`

## Purpose
Single upstream caption (comment) row as returned by `GET /api/captions/history`. Mirrors upstream snake_case LiveHub field names.

## Public API
Record `CaptionEntry(@JsonProperty("_id") String id, @JsonProperty("user_id") long userId, @JsonProperty("nick_name") String nickName, @Nullable String nationality, String text, @JsonProperty("create_at") long createAt)`:
- `id` — upstream `_id` string.
- `userId` — upstream `user_id`.
- `nickName` — upstream `nick_name`.
- `nationality` — nullable flag.
- `text` — caption body.
- `createAt` — upstream `create_at`, Unix seconds.

## Coupling
- Imported by: `CaptionHistoryResponse` (sibling, same package).
- Imports: `com.fasterxml.jackson.annotation.JsonProperty`, `io.micronaut.core.annotation.Nullable`, `io.micronaut.serde.annotation.Serdeable`.

## Notes
- `createAt` is Unix **seconds** (upstream convention) — frontend will receive ms via the `CamelCaseResponseFilter` rename `create_at` → `createAt`, but the unit is still seconds, so any `new Date(value)` on the client will be off by ×1000. Inconsistent with `CommentDto`/`CommentListDto` which actively convert to ms.
- Inconsistent `nick_name` vs `nickname` naming vs `Comment.nickname` (camelCase).
