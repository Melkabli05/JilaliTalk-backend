# CommentListDto.java

`src/main/java/com/jilali/comment/dto/CommentListDto.java`

## Purpose
BFF response wrap for the comment list (millisecond timestamps). Returned by `GET /api/comments` and embedded inside the room join bundle.

## Public API
Record `CommentListDto(@Nullable List<CommentDto> items, @JsonProperty("has_next") boolean hasNext, @JsonProperty("oldest_id") @Nullable String oldestId)`:
- `items` — nullable upstream page; `items()` accessor normalizes null to `List.of()`.
- `hasNext` — upstream `has_next` flag.
- `oldestId` — cursor (oldest id in this page) for pagination.

## Coupling
- Imported by: `CommentController.comments` (constructs), `RoomJoinService` (constructs), `JoinBundleResponse` (carries as a bundle sub-payload).
- Imports: `com.fasterxml.jackson.annotation.JsonProperty`, `io.micronaut.core.annotation.Nullable`, `io.micronaut.serde.annotation.Serdeable`, `java.util.List`.

## Notes
- Duplicate logic: structurally identical to upstream `CommentListResponse` except `Comment` → `CommentDto`. The `CommentController` and `RoomJoinService` both hand-unroll `CommentListResponse` → `CommentListDto` via `.stream().map(this::toDto).toList()` — same boilerplate, two copies.
- The `items()` accessor overriding the canonical record accessor to coalesce null is the same defensive idiom used in `CaptionHistoryResponse` and `CommentListResponse` — minor pattern duplication, could move to a static helper.
- Per `SendCommentResponse`'s Javadoc, several `@JsonProperty` renames on the sibling `CommentDto` are not honored by the running server — `oldest_id` may not be renamed either; worth verifying via curl.
