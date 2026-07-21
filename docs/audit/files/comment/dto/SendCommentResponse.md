# SendCommentResponse.java

`src/main/java/com/jilali/comment/dto/SendCommentResponse.java`

## Purpose
Response body for `POST /api/comments`. Returns the BFF's own send instant plus a best-effort upstream id so the frontend can reconcile its optimistic insert.

## Public API
Record `SendCommentResponse(long createdAtMs, @Nullable String id)`:
- `createdAtMs` — always populated (the BFF's send instant, the same value used to build `send_time` upstream).
- `id` — best-effort; only populated when the upstream response happens to include one. Caller must not depend on it.

## Coupling
- Imported by: `CommentController.sendComment` (return type, `@Body`).
- Imports: `io.micronaut.core.annotation.Nullable`, `io.micronaut.serde.annotation.Serdeable`.

## Notes
- Deliberately **no `@JsonProperty` renames** — the Javadoc explicitly cites that sibling DTO renames (e.g. on `CommentDto`) are not honored by the running server; plain field names sidestep that uncertainty. Worth verifying the live behavior matches the Javadoc claim, but the rationale is sound.
- Minimal, intentional surface — only two fields. Good shape for a contract that promises as little as possible.
- `id` is `String` (matching upstream `_id`), not a typed `CommentId` value object — consistent with the rest of the package.
