# CommentDto.java

`src/main/java/com/jilali/comment/dto/CommentDto.java`

## Purpose
BFF response shape for a single comment. Server-converted variant of `Comment`: timestamps × 1000 (Unix-seconds → milliseconds) so Angular can build `Date`/`Number` without further transform.

## Public API
Record `CommentDto(@JsonProperty("_id") @Nullable String id, @JsonProperty("created_at_ms") long createdAtMs, @JsonProperty("updated_at_ms") long updatedAtMs, @JsonProperty("cname") @Nullable String cname, @JsonProperty("busi_type") int busiType, @JsonProperty("user_id") long userId, @Nullable String nickname, @JsonProperty("head_url") @Nullable String headUrl, @Nullable String nationality, int role, @JsonProperty("vip_type") int vipType, @Nullable Msg msg, @JsonProperty("day_rank_level") int dayRankLevel, @JsonProperty("gift_level") int giftLevel, @JsonProperty("fg_level") int fgLevel, @JsonProperty("fg_name") @Nullable String fgName, @JsonProperty("fg_is_active") boolean fgIsActive, @JsonProperty("bubble_id") int bubbleId, @JsonProperty("bubble_url") @Nullable String bubbleUrl, @JsonProperty("bubble_color") @Nullable String bubbleColor, @JsonProperty("hit_bad") int hitBad, @JsonProperty("bubble_animal_type") int bubbleAnimalType, @JsonProperty("bubble_animal_url") @Nullable String bubbleAnimalUrl, @JsonProperty("vip_logo") @Nullable String vipLogo, @JsonProperty("vip_logo_anim") @Nullable String vipLogoAnim, @Nullable String expireAt, @JsonProperty("medal_wall_icon") @Nullable String medalWallIcon)` — 26 fields; `createdAtMs`/`updatedAtMs` Unix **milliseconds**.
- Nested `Msg(@Nullable Text text, @Nullable ReplyInfo replyInfo)`, `Text(@Nullable String text)`, `ReplyInfo(@Nullable String msgId, long fromId, @Nullable String fromNickname, @Nullable String text, @Nullable String msgType)` — camelCase, no `@JsonProperty`.

## Coupling
- Imported by: `CommentController` (via `toDto`), `RoomJoinService` (via `toCommentDto` — same mapper duplicated), `CommentListDto` (sibling wrap).

## Notes
- **Duplicate logic with `Comment`**: see `Comment.md` for the full 28-field parallel. Eliminating this DTO via a custom Serde serializer would erase the duplicated `toDto`/`toCommentDto` mappers.
- Per `SendCommentResponse`'s Javadoc, many `@JsonProperty` renames on this record are not honored by the live server (curl evidence) — `CamelCaseResponseFilter` does the outbound camelCase conversion anyway, making these overrides arguably dead-letter intent.
- Inconsistent with sibling `BffSendCommentRequest.ReplyInfo` (plain camelCase) — three `ReplyInfo` records across this package disagree on casing.
