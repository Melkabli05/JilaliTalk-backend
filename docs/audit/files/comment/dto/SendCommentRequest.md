# SendCommentRequest.java

`src/main/java/com/jilali/comment/dto/SendCommentRequest.java`

## Purpose
Upstream wire shape for `POST /comments`. Snake_case body the BFF assembles in `CommentController.sendComment` and forwards via `JilaliClient`.

## Public API
Record `SendCommentRequest(@NotBlank String cname, @JsonProperty("busi_type") int busiType, String nickname, @JsonProperty("head_url") @Nullable String headUrl, @Nullable String nationality, int role, Msg msg)`:
- `cname`, `busiType`, `nickname`, `headUrl`, `nationality`, `role` — top-level sender metadata.
- `Msg(@JsonProperty("msg_type") String msgType, @JsonProperty("msg_model") String msgModel, @JsonProperty("send_time") String sendTime, @JsonProperty("from_nickname") @Nullable String fromNickname, String source, Text text, @JsonProperty("reply_info") @Nullable ReplyInfo replyInfo)` — message envelope.
- `Msg.Text(String text)` — body.
- `Msg.ReplyInfo(@JsonProperty("msg_id") String msgId, @JsonProperty("from_id") long fromId, @JsonProperty("from_nickname") String fromNickname, String text, @JsonProperty("msg_type") String msgType)` — reply-quote.

## Coupling
- Imported by: `CommentController.sendComment` (builds), `JilaliClient` (sends upstream).
- Imports: `com.fasterxml.jackson.annotation.JsonProperty`, `io.micronaut.core.annotation.Nullable`, `io.micronaut.serde.annotation.Serdeable`, `jakarta.validation.constraints.NotBlank`.

## Notes
- Duplicate logic: `SendCommentRequest.Msg.ReplyInfo` and `BffSendCommentRequest.ReplyInfo` are near-identical 5-field records with the only delta being snake_case vs camelCase field names. Controller hand-maps between them.
- Validation is partial: only `cname` is `@NotBlank`; `nickname`, `Msg`/`Text`/`ReplyInfo` fields are unannotated. Inconsistent with `BffSendCommentRequest` which validates `cname`/`nickname`/`text`.
- Hardcoded message-shape literals (`"normal"`, `"text"`, `""`) live in `CommentController.sendComment`, not on the DTO — fragile split between data and shape.
