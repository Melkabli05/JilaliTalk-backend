# CommentController.java

`src/main/java/com/jilali/comment/CommentController.java`

## Purpose
HTTP controller for comments and captions under `/api`. Fetches/sends comments and caption history/switch via `JilaliClient`, and — critically — performs the upstream→frontend DTO mapping including Unix-seconds→milliseconds timestamp conversion and reply-info shape translation.

## Responsibilities
- `GET /api/captions/history`, `POST /api/captions/switch` — thin pass-throughs.
- `GET /api/comments` — unwrap upstream `CommentListResponse`, map each `Comment` → `CommentDto` (×1000 timestamps), wrap in `CommentListDto`.
- `POST /api/comments` — map camelCase `BffSendCommentRequest` → snake_case `SendCommentRequest`, stamp `send_time`, call upstream, best-effort id extraction, return `SendCommentResponse` with the BFF's own send instant.

## Public API
Class `CommentController`, `@ExecuteOn(BLOCKING)`, `@Controller("/api")`:
- Constructor `(JilaliClient client)`.
- `CaptionHistoryResponse history(int busiType=2, @NotBlank String cname, int pageSize=20)`
- `HttpResponse<Void> switchCaption(@Valid CaptionSwitchRequest)`
- `CommentListDto comments(int busiType=2, @NotBlank String cname)`
- `HttpResponse<SendCommentResponse> sendComment(@Valid BffSendCommentRequest)`
- Private helpers: `toDto(Comment)`, `toMsgDto(Comment.Msg)`, `toReplyInfoDto(Comment.Msg.ReplyInfo)`, `extractId(Object)`; static `SEND_TIME_FMT`.

## Dependencies
- Injects `JilaliClient`; uses `JilaliResponses`, all comment DTOs, `java.time`.
- Depended on by: nothing internal (HTTP entrypoint). `@Controller` methods are not dead by grep.

## Coupling and cohesion analysis
Cohesive around the comment/caption domain. Coupling to `JilaliClient` directly (no service layer) — reasonable for pass-throughs but the DTO-mapping logic (`toDto` and friends) is business/transform logic sitting in the controller. Mixed altitude: thin caption pass-throughs coexist with a 28-field manual mapper.

## Code smells
- **Long Method / manual mapping boilerplate**: `toDto` (lines 74–83) copies **28 fields** one-by-one from `Comment` to `CommentDto`. Pure mechanical field-for-field copy differing only in the two `*1000L` timestamps. Classic Data-Class-mapping smell.
- **Feature Envy**: `toDto`/`toMsgDto`/`toReplyInfoDto` operate almost entirely on `Comment`'s fields — the mapping arguably belongs on the DTO or a dedicated mapper, not the controller.
- **Missing service layer**: controller calls `client` directly and holds transform logic; no `CommentService`.
- **Primitive Obsession**: `int busiType` default `2` as magic literal (repeated in `history` and `comments`).

## Technical debt
- The 28-field `toDto` mapper exists *only* because `Comment` and `CommentDto` are near-identical records differing solely in timestamp units and field names (`created_at` vs `created_at_ms`). See CommentDto/Comment duplicate analysis.
- `extractId` (142–146) does untyped `instanceof Map` reflection on an undocumented upstream response shape — fragile, best-effort by design but brittle.
- `sendComment` hardcodes `"text"`, `"normal"`, `""` message-envelope literals inline.

## Duplicate logic
- `toDto` is the visible cost of the **Comment ↔ CommentDto duplication** (28 parallel fields). `toMsgDto`/`toReplyInfoDto` similarly bridge `Comment.Msg`↔`CommentDto.Msg` and `SendCommentRequest.Msg.ReplyInfo`↔... — four near-identical `ReplyInfo` shapes exist across the DTOs (see package doc).
- The camelCase→snake_case field mapping in `sendComment` partly duplicates what `CamelCaseResponseFilter` does in reverse for responses.

## Dead or unused code
None. All methods are `@Controller` routes; helpers are all called.

## Java 25 modernization opportunities
- **`toMsgDto`/`toReplyInfoDto` null-guards** could remain, but the whole `toDto` chain is the prime candidate to disappear if `Comment` and `CommentDto` are unified (see below).
- `extractId` could use a record pattern / pattern-matching `switch` instead of `instanceof Map<?,?> map` (already uses pattern instanceof at 143 — good; a `switch` pattern would generalize if more shapes were handled).

## Micronaut built-in opportunities
- The Unix→ms conversion could be a Micronaut Serde `@Serdeable` custom serializer on the timestamp field instead of a manual `*1000` in the controller, letting `Comment` serialize directly and removing `CommentDto` + `toDto` entirely.
- `CamelCaseResponseFilter` already normalizes outbound keys, so `CommentDto`'s `@JsonProperty("created_at_ms")` renames are partly redundant (they get camelCased to `createdAtMs` on the way out anyway — documented in `SendCommentResponse`).

## Refactoring recommendations
1. **Eliminate `CommentDto`/`CommentListDto`**: keep one `Comment` record and do the ms conversion via a serde serializer or a single computed accessor, deleting the 28-field `toDto` mapper.
2. Extract a `CommentService` to hold the send-request assembly and id extraction, leaving the controller thin.
3. Replace magic `busiType=2` with a `BusiType` enum default.
