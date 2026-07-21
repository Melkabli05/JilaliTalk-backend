# FollowersResponse

`src/main/java/com/jilali/user/dto/FollowersResponse.java` (43 lines)

## Purpose
Response from `GET /relation/followers`. Uses the `status`/`message` envelope (NOT LiveHub `code`/`msg`).

## Responsibilities
- Wrap a paginated list of follower mini-profiles plus a pinned-follows stat.

## Public API
- `int status`, `String message`, `@Nullable FollowersData data`.
  - `FollowersData`: `String pageIndex` (`page_index`), `boolean more`, `int count`, `@Nullable PinnedStat pinnedStat`, `@Nullable List<FollowerUser> list`.
  - `PinnedStat`: `int limit`, `int cnt`.
  - `FollowerUser`: `long userId` (`user_id`), `int sex`, `@Nullable String nationality`, `@Nullable String headUrl` (`head_url`), `@Nullable String nickName` (`nick_name`), `@Nullable Integer nativeLang` (`native_lang`), `@Nullable Integer vipType`, `int giftLevel`, `@Nullable String remarkName`, `boolean isMutual` (`is_mutual`).

## Dependencies
Depended on by `ProfileController.followers` and `ProfileClient`.

## Coupling and cohesion analysis
Cohesive paginated-list envelope. The nested `FollowerUser`/`PinnedStat` are self-contained.

## Code smells
- **Blatant copy-paste**: `PinnedStat` and `FollowerUser` are byte-for-byte identical to the ones nested in `FollowingResponse`.

## Technical debt
- Two independent definitions of `FollowerUser`/`PinnedStat` (here and in `FollowingResponse`) must be kept in sync manually.

## Duplicate logic
- **`FollowersResponse` and `FollowingResponse` are near-identical files** — same `status/message/data` envelope; `FollowersData` and `FollowingData` have identical fields (`pageIndex, more, count, pinnedStat, list`); `PinnedStat` and `FollowerUser` are duplicated verbatim. This is the single clearest exact-duplicate pair in the package.
- `FollowerUser`'s field set (`userId, sex, nationality, headUrl, nickName, nativeLang, vipType, giftLevel, remarkName`) overlaps heavily with the recurring "mini user profile" shape in `VisitorsResponse.VisitorUser`, `ProfileMeResponse.UserInfo`, and `room.dto.RoomUser`.

## Dead or unused code
Live — returned by `ProfileController.followers`.

## Refactoring recommendations
1. Extract a single shared `RelationListResponse` (or generic `PagedRelation<T>`) with one `FollowerUser` and one `PinnedStat`, used by both followers and followings.
