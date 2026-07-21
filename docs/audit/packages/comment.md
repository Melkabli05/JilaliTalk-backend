# `com.jilali.comment` + `comment.dto` — voice-room comments + live captions

## Purpose

Two related sub-features for voice rooms: `Comments` (user-posted text comments shown on the room's comment feed) and `Captions` (live speech-to-text captions toggled per-language).

## File responsibilities (1 controller + 10 dto = 11 files)

### Root

| File | One-line summary |
|---|---|
| `CommentController.java` | `/api/comments/*` and `/api/captions/*`. Implements `seconds → milliseconds` timestamp conversion in private helpers, plus a manual-`Object`-to-`Map` extraction of the upstream send response. |

### DTOs (10)

Two clusters sharing shapes:
- **`Comment` ↔ `CommentDto`**: 28 fields, near-exact duplicates differing only in timestamp unit (Unix-seconds upstream, Unix-ms outbound) and one casings. Forced to maintain hand-written mappers in two places.
- **`CaptionEntry`**, `CaptionHistoryResponse`, `CaptionSwitchRequest`: caption lifecycle.

All other DTOs (`BffSendCommentRequest`, `CommentListDto`, `CommentListResponse`, `SendCommentRequest`, `SendCommentResponse`) are request/response envelope shapes.

## Dependencies

- **Inbound**: Angular frontend consumes the REST endpoints.
- **Outbound**: `JilaliClient` (declarative Micronaut HTTP client), `JilaliResponses.unwrap` (envelope unwrapping).

## ⚠ Comments and Findings from the audit

- **`Comment` ↔ `CommentDto` is the largest concrete duplication in the codebase outside `im`/`realtime`**: 28 fields, two parallel mappers in `CommentController.toDto` AND `RoomJoinService.toCommentDto`. A custom `Serde` serializer on the timestamp fields would eliminate both classes and both mappers.
- **Inconsistent `@JsonProperty` casing across three `ReplyInfo` records**: half use snake_case, half plain camelCase. `@JsonProperty` renames on `CommentDto` are also effectively dead-letter because `CamelCaseResponseFilter` overrides them on outbound.
- **Timestamp unit inconsistency**: `Comment.createdAt`/`updatedAt` are Unix-seconds, `CommentDto` are ms; `CaptionEntry.createAt` is also seconds but does NOT get the ms conversion applied downstream — silently wrong timestamp.
- **`expireAt` is `String` while sibling timestamp fields are `long`** — odd outlier in `Comment`/`CommentDto`.
- **Missing `null` discipline**: `expireAt` is a string-encoded Unix timestamp without a documented format, `Card_feature.card_type` (in vip) is `String` here but `int` elsewhere — drift that suggests hand-porting from upstream wire specs without type discipline.
- **`Comment.Msg.ReplyInfo` historical bug documented in its Javadoc**: GET path lost reply-quote context for historical comments.

## Improvement opportunities

1. **High**: collapse `Comment` + `CommentDto` into a single record shared between the client interface and the Angular response shape — replace both mappers with a single Serde timestamp serializer.
2. **Medium**: standardize `ReplyInfo` field casing across all three records via `micronaut-serde` naming strategy.
3. **Medium**: persist an upstream-wire-shape reference doc (it's referenced in user audit too) so the JSON-property annotations are written against an actual contract, not guessed.
4. **Low**: when `CamelCaseResponseFilter` is consistently applied on outbound, the `@JsonProperty` renames on outbound-bound DTOs are dead-letter — drop them.
