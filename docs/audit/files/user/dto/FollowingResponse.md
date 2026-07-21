# FollowingResponse

`src/main/java/com/jilali/user/dto/FollowingResponse.java` (43 lines)

## Purpose
Response from `GET /relation/followings`. Uses the `status`/`message` envelope (NOT LiveHub `code`/`msg`).

## Responsibilities
- Wrap a paginated list of following mini-profiles plus a pinned stat.

## Public API
- `int status`, `String message`, `@Nullable FollowingData data`.
  - `FollowingData`: `String pageIndex` (`page_index`), `boolean more`, `int count`, `@Nullable PinnedStat pinnedStat`, `@Nullable List<FollowerUser> list`.
  - `PinnedStat`: `int limit`, `int cnt`.
  - `FollowerUser`: identical to `FollowersResponse.FollowerUser` (`userId, sex, nationality, headUrl, nickName, nativeLang, vipType, giftLevel, remarkName, isMutual`).

## Dependencies
Depended on by `ProfileController.following` and `ProfileClient`.

## Coupling and cohesion analysis
Cohesive. Structurally indistinguishable from `FollowersResponse` apart from the outer/inner record names (`FollowingData` vs `FollowersData`).

## Code smells
- **Blatant copy-paste of `FollowersResponse`** — `PinnedStat` and `FollowerUser` duplicated verbatim; `FollowingData` mirrors `FollowersData` field-for-field.

## Technical debt
- Two hand-synced copies of the same list-envelope structure.

## Duplicate logic
- **Exact-duplicate pair with `FollowersResponse`** (see that file). The only meaningful difference is upstream URL (`/relation/followers` vs `/relation/followings`) and the wrapper record name. `FollowerUser` and `PinnedStat` are defined identically in both.
- `FollowerUser` overlaps the recurring mini-profile shape (see `FollowersResponse.md`).

## Dead or unused code
Live — returned by `ProfileController.following`.

## Refactoring recommendations
1. Collapse `FollowersResponse` + `FollowingResponse` into one `RelationListResponse` (or `PagedRelation<FollowerUser>`); delete the duplicate nested records.
