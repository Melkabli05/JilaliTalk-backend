# RoomUserProfileResponse

`src/main/java/com/jilali/user/dto/RoomUserProfileResponse.java` (43 lines)

## Purpose
Decoded body of `GET livehub/user/profile?cname=...&user_id=...` — the room-scoped per-target-user profile lookup. Wire format is `bin/cc2018` (decoded by `JilaliGateway.roomUserProfile` before deserialization). Only the follow relation is mapped.

## Responsibilities
- Expose the viewer↔target follow relation from an otherwise-large upstream profile payload, dropping everything unmapped.

## Public API
- `int code`, `@Nullable String msg`, `@Nullable Data data`. `@JsonIgnoreProperties(ignoreUnknown = true)`.
  - `Data`: `@Nullable FollowStat followStat` (`follow_stat`).
  - `FollowStat`: `int status` (0=not following, 1=following, 2=mutual), `int folowerStatus` (`folower_status` — upstream's own misspelling; reverse direction).

## Dependencies
Depended on by `UserController.profile` and `JilaliGateway`.

## Coupling and cohesion analysis
Cohesive, deliberately minimal projection. `@JsonIgnoreProperties` makes it robust to the large unmapped upstream payload.

## Code smells
- **`code`/`msg` envelope** but with `@Nullable msg` (inconsistent with `BlockListResponse`'s non-null `msg`).
- **Primitive Obsession**: follow relation as raw `int` sentinels rather than an enum.

## Technical debt
- Only `follow_stat` is mapped; adding fields later means re-deriving the cc2018 shape.

## Duplicate logic
- Despite the `RoomUser` name prefix, this shares NOTHING structurally with `RoomUserListResponse` (a roster) or `RoomUserListRequest`. It is a follow-relation lookup.
- **`FollowStat` overlaps other follow-relation encodings**: `FollowersResponse.FollowerUser.isMutual` (boolean) and the numeric follow states here (0/1/2) express the same "am I following / mutual" concept in different encodings. A shared `FollowRelation` enum could unify them.

## Dead or unused code
Live — returned by `UserController.profile`.

## Refactoring recommendations
1. Model `status`/`folowerStatus` as a `FollowRelation` enum shared with `FollowerUser.isMutual`.
2. Fold into `CodeMsgEnvelope<Data>` (with nullable `msg`).
