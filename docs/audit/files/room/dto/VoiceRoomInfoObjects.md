# VoiceRoomInfoObjects.java

`src/main/java/com/jilali/room/dto/VoiceRoomInfoObjects.java`

## Purpose
Namespace holder for the many nested sub-object records used by the LiveHub `voice_room_info` response (`VoiceRoomInfoResponse`).

## Responsibilities
Group otherwise-standalone nested DTOs: `PinnedComment` (+ nested `CommentClosedFriend`, `Msg`/`TextPayload`, `UserExtraInfo`), `SayGuessInfo` (+ `Question`/`Option`), `WhiteboardInfo`, `WhiteboardSettings`, `LuckBag`, `RoomBackground`, `CaptionInfo` (+ `TranslateLang`), `UserPaidExposureData`, `QuickChatInfo`.

## Public API
- Final class, private constructor (non-instantiable holder).
- Public nested `@Serdeable` records (each `@JsonIgnoreProperties(ignoreUnknown = true)`): `PinnedComment`, `PinnedComment.CommentClosedFriend`, `PinnedComment.Msg`, `PinnedComment.Msg.TextPayload`, `PinnedComment.UserExtraInfo`, `SayGuessInfo`, `SayGuessInfo.Question`, `SayGuessInfo.Question.Option`, `WhiteboardInfo`, `WhiteboardSettings`, `LuckBag`, `RoomBackground`, `CaptionInfo`, `CaptionInfo.TranslateLang`, `UserPaidExposureData`, `QuickChatInfo`. Fields per-record as declared (all upstream snake_case mapped).

## Dependencies
- Imports `@JsonIgnoreProperties`, `@JsonProperty`, `@Nullable`, `@Serdeable`.
- Consumed by `VoiceRoomInfoResponse.ChannelInfo` and top-level fields; also referenced by `realtime.dto.RoomRealtimeEvent`.

## Coupling and cohesion analysis
Cohesion is **organisational, not logical** — it is a grab-bag of unrelated room sub-features (games, whiteboard, captions, backgrounds, luck bag, quick chat) bundled only because they share the `voice_room_info` parent. Low logical cohesion, but a reasonable pragmatic namespace.

## Code smells
- **Large Class / God File** (by aggregation): 16 record types, ~190 lines, many unrelated concerns.
- **PinnedComment near-duplicates `comment.dto.Comment`**: `PinnedComment` carries ~30 comment fields (id, createdAt, userId, nickname, msg, bubble*, vip*, fg*, medalWallIcon...) that strongly overlap the `Comment`/`CommentDto` shape mapped in `RoomJoinService.toCommentDto` — likely the same upstream comment entity modeled twice.
- **Primitive Obsession**: pervasive int status/type codes.

## Technical debt
`PinnedComment` vs `comment.dto.Comment` duplication means a comment schema change must be made in two places.

## Duplicate logic
`PinnedComment` overlaps `comment.dto.Comment`/`CommentDto` (bubble/vip/fg/medal fields, msg text/reply structure). Its identity fields also repeat the `{userId, nickname, headUrl, nationality}` tuple.

## Dead or unused code
None flagged as dead (all reachable via `VoiceRoomInfoResponse`), though whether every nested field is consumed by the frontend is unverified — records are Jackson/Serde-invoked.

## Refactoring recommendations
- Split into topical files (`gameinfo`, `whiteboard`, `caption`, `background` sub-packages) if this grows further.
- Reconcile `PinnedComment` with `comment.dto.Comment` — model the comment entity once.
