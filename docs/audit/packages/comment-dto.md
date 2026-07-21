# `com.jilali.comment.dto` — comment & caption shapes

## Files (10)

Per-record purpose:

| DTO | Shape | Notes |
|---|---|---|
| `BffSendCommentRequest` | `{cname, busiType, nickname, headUrl, nationality, role, text, replyInfo?, bubbleId?, bubbleColor?}` | Browser-facing inbound (validated). |
| `CaptionEntry` | One captioning history entry. `createAt` is **Unix seconds** (inconsistent with `Comment.created_at`-ms convention used elsewhere — flag). |
| `CaptionHistoryResponse` | `{items: List<CaptionEntry>?, hasNext}` wrapper. |
| `CaptionSwitchRequest` | Caption enable/disable body. |
| `Comment` | The upstream wire shape: 28 fields, all top-level (`cname`, `busiType`, `userId`, timestamp-as-Unix-**seconds** + a nested `Msg {text, replyInfo}`). | **Near-exact duplicate** of `CommentDto` differing only in timestamp-unit (sec vs ms) + one casing — the #2 DTO duplication in the codebase after `RewardItem`. `Comment.Msg.ReplyInfo` historically missing on the GET path (Javadoc-documented bug). |
| `CommentDto` | The BFF outbound shape: 28 fields parallel to `Comment` but with `created_at_ms`/`updated_at_ms` in milliseconds. Used by `CommentListDto` returned to the Angular frontend. | **`@JsonProperty` renames here are effectively dead-letter** — `CamelCaseResponseFilter` overrides them on outbound. |
| `CommentListDto` | `{items: List<CommentDto>, hasNext: boolean, oldestId}` wrapper. Frontend-facing. |
| `CommentListResponse` | The upstream wire-shape sibling — `{items: List<Comment>, hasNext: long, oldestId: long}`. | **Structurally identical to `CommentListDto`** modulo timestamp unit / casing. |
| `SendCommentRequest` | The BFF→upstream wire payload, hand-constructed in `CommentController.sendComment`. |
| `SendCommentResponse` | `{sentAtMs: long, upstreamCommentId: String?}` — BFF-authored response carrying its own sent-at instant for the frontend's optimistic-insert reconciliation. |

## Dependencies

- Used as request/response types by `CommentController` (`/api/comments/*`, `/api/captions/*`).
- `Comment` is the upstream wire shape — imported in `JilaliClient`'s `comments()` / `captionHistory()` / `sendComment()` declarative interface methods.
- `CommentDto` is the browser-facing response shape.

## ⚠ Top issues

1. **28-field duplicate `Comment` ↔ `CommentDto`** forces a hand-rolled mapper in two places (controller AND `RoomJoinService.toCommentDto`). A custom `Serde` serializer on a single shared record eliminates both.
2. **Inconsistent timestamp units across files**: `Comment.createdAt`/`updatedAt` seconds, `CommentDto` ms, `CaptionEntry.createAt` seconds but not converted downstream — silently wrong in caption UI.
3. **Mixed `@JsonProperty` casing**: `BffSendCommentRequest.ReplyInfo` plain camelCase, `SendCommentRequest.Msg.ReplyInfo` snake_case, `Comment.Msg.ReplyInfo` snake_case, `CommentDto.Msg.ReplyInfo` plain camelCase.
4. **`expireAt` is `String`** while sibling timestamp fields are `long` in `Comment`/`CommentDto`.

## Improvement opportunities

1. **High**: collapse `Comment` + `CommentDto` via a single record + Serde timestamp-unit serializer. Eliminates both mappers.
2. **Medium**: standardize all `ReplyInfo` shapes — one record, snake_case via `@JsonProperty`.
3. **Low**: pick a timestamp convention (ms everywhere) and add conversion only at the wire-boundary.
