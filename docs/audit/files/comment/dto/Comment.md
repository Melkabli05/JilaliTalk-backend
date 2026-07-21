# Comment.java

`src/main/java/com/jilali/comment/dto/Comment.java`

## Purpose
Upstream wire shape of a single comment from LiveHub. Snake_case fields, Unix-**second** timestamps. Deserialized by `JilaliClient` from the GET response.

## Public API
Record `Comment(@JsonProperty("_id") @Nullable String id, @JsonProperty("created_at") long createdAt, @JsonProperty("updated_at") long updatedAt, @JsonProperty("cname") @Nullable String cname, @JsonProperty("busi_type") int busiType, @JsonProperty("user_id") long userId, @Nullable String nickname, @JsonProperty("head_url") @Nullable String headUrl, @Nullable String nationality, int role, @JsonProperty("vip_type") int vipType, @Nullable Msg msg, @JsonProperty("day_rank_level") int dayRankLevel, @JsonProperty("gift_level") int giftLevel, @JsonProperty("fg_level") int fgLevel, @JsonProperty("fg_name") @Nullable String fgName, @JsonProperty("fg_is_active") boolean fgIsActive, @JsonProperty("bubble_id") int bubbleId, @JsonProperty("bubble_url") @Nullable String bubbleUrl, @JsonProperty("bubble_color") @Nullable String bubbleColor, @JsonProperty("hit_bad") int hitBad, @JsonProperty("bubble_animal_type") int bubbleAnimalType, @JsonProperty("bubble_animal_url") @Nullable String bubbleAnimalUrl, @JsonProperty("vip_logo") @Nullable String vipLogo, @JsonProperty("vip_logo_anim") @Nullable String vipLogoAnim, @Nullable String expireAt, @JsonProperty("medal_wall_icon") @Nullable String medalWallIcon)` — 26 fields; `createdAt`/`updatedAt` Unix seconds.
- Nested `Msg(@Nullable Text text, @JsonProperty("reply_info") @Nullable ReplyInfo replyInfo)`, `Text(@Nullable String text)`, `ReplyInfo(@JsonProperty("msg_id") @Nullable String msgId, @JsonProperty("from_id") long fromId, @JsonProperty("from_nickname") @Nullable String fromNickname, @Nullable String text, @JsonProperty("msg_type") @Nullable String msgType)`.

## Coupling
- Imported by: `CommentController` (inbound, mapped to `CommentDto`) and `RoomJoinService` (same mapping — **duplicated**).

## Notes
- **Massive duplicate logic with `CommentDto`**: 28 parallel fields differing only in timestamp-unit (sec vs ms × 1000) and the `reply_info`/`replyInfo` snake-vs-camel rename on `Msg`. This forces a 28-field hand-written mapper in **both** `CommentController.toDto` and `RoomJoinService.toCommentDto`. A custom Micronaut Serde serializer for the timestamp field would let one record serve both purposes and erase both mappers.
- `expireAt` is `String` (not `Instant`/`long`) — outlier among timestamp-shaped fields.
- `Msg.ReplyInfo` Javadoc flags a known historical bug: this record was added late, so older GET fetches silently lost reply-quote context.
