# BffSendCommentRequest.java

`src/main/java/com/jilali/comment/dto/BffSendCommentRequest.java`

## Purpose
Inbound request body for `POST /api/comments`. Angular BFF client payload in camelCase; controller re-maps to snake_case `SendCommentRequest` before upstream call.

## Public API
Record `BffSendCommentRequest(@NotBlank String cname, int busiType, @NotBlank String nickname, @Nullable String headUrl, @Nullable String nationality, int role, @NotBlank String text, @Nullable ReplyInfo replyInfo)`:
- `ReplyInfo(String msgId, long fromId, String fromNickname, String text, String msgType)` — optional reply-quote context.

## Coupling
- Imported by: `CommentController` only.
- Imports: `io.micronaut.core.annotation.Nullable`, `io.micronaut.serde.annotation.Serdeable`, `jakarta.validation.constraints.NotBlank`.

## Notes
- The `ReplyInfo` here is camelCase (no `@JsonProperty`); sibling `SendCommentRequest.Msg.ReplyInfo` is snake_case — same conceptual payload, divergent shapes, manually bridged in the controller.
- Duplicate logic: `BffSendCommentRequest.ReplyInfo` and `SendCommentRequest.Msg.ReplyInfo` are near-identical 5-field records; only the casing differs.
- Plain field names here (no `@JsonProperty` renames) sidestep the unverified-rename risk called out in `SendCommentResponse`.
