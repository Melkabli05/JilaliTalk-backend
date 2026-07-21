# CommentListResponse.java

`src/main/java/com/jilali/comment/dto/CommentListResponse.java`

## Purpose
Upstream wire wrap for the paginated comment list. Deserialized by `JilaliClient` from LiveHub's GET response; unwrapped and re-wrapped by the controller/service as `CommentListDto`.

## Public API
Record `CommentListResponse(@Nullable List<Comment> items, @JsonProperty("has_next") boolean hasNext, @JsonProperty("oldest_id") @Nullable String oldestId)`:
- `items` — nullable upstream page; `items()` accessor normalizes null to `List.of()`.
- `hasNext` — upstream `has_next`.
- `oldestId` — upstream `oldest_id`, pagination cursor.

## Coupling
- Imported by: `JilaliClient` (deserialized from upstream), `RoomJoinService` (consumed and re-wrapped).
- Imports: `com.fasterxml.jackson.annotation.JsonProperty`, `io.micronaut.core.annotation.Nullable`, `io.micronaut.serde.annotation.Serdeable`, `java.util.List`.

## Notes
- Duplicate logic: identical shape to `CommentListDto` (only `Comment` → `CommentDto` differs). Both `CommentController.comments` and `RoomJoinService` consume this, then re-build a parallel `CommentListDto`.
- `items()` accessor overriding the canonical accessor to coalesce null duplicates the same pattern in `CommentListDto` and `CaptionHistoryResponse`.
- `oldestId` / `oldest_id` is nullable — page-1 callers may legitimately get null; not a smell, but worth flagging for pagination consumers.
