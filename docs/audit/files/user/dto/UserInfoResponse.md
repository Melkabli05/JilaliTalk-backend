# UserInfoResponse

`src/main/java/com/jilali/user/dto/UserInfoResponse.java` (261 lines)

## Purpose
Mirrors the encrypted HelloTalk userinfo response (`/profile/v2/userinfo`, `ht/encbin`). The full, faithful upstream profile shape. `code`/`msg`/`data` envelope. The largest DTO in the package.

## Responsibilities
- Deserialize the complete upstream profile (base, points, tags, location, relation, privileges, online/live state, defaults, gift level, pay info, remark).
- Flatten the first item into the clean `UserInfo` via `toUserInfo()` (with `flattenTags` and `mapSex`).

## Public API
- Envelope: `int code`, `@Nullable String msg`, `@Nullable UserInfoData data`.
  - `UserInfoData`: `List<UserInfoItem> list`.
  - `UserInfoItem`: `userId` + 12 nullable nested sections (`base, points, tags, location, relation, privileges, onlineState, liveState, defaults, giftLevel, payInfo, remark`).
  - Nested records: `BaseInfo` (~40 fields incl. VIP/bubble cosmetics), `LangInfo`, `UserExtraInfo`, `PointsInfo` (9 categories), `TagsInfo` (8 tag arrays), `TagItem`, `LocationInfo`, `RelationInfo`, `PrivilegesInfo`, `OnlineStateInfo`, `LiveStateInfo`, `DefaultInfo`, `PayInfo`, `RemarkInfo`.
- `UserInfo toUserInfo()` — flattens `list.get(0)`; returns null on empty.

## Dependencies
Depended on by `UserInfo` (references `UserInfoItem` as `details`), `ImEventEnricher`, `JilaliGateway`. Paired with `UserInfoRequest`.

## Coupling and cohesion analysis
Cohesive as "the whole upstream profile," but ENORMOUS — ~15 nested records, ~100 fields. `toUserInfo()` gives it a second role (mapper), coupling it to `UserInfo`.

## Code smells
- **Large Class / God DTO**: ~100 fields across 15 nested records in one file.
- **Primitive Obsession** throughout (langs/levels/timestamps/flags as raw Integer/Long/int).
- **Boolean-as-Integer**: many flags are `@Nullable Integer` (0/1) rather than `boolean` (`allowLocation`, `hideAge`, `isScammer`, etc.).
- **Mapper on a DTO**: `toUserInfo()`/`flattenTags()` are mapping logic living in the response record.

## Technical debt
- Nullability is inconsistent even within: `msg` nullable here vs non-null in `BlockListResponse`.
- The 8-category `TagsInfo` vs `ProfileLimitationsResponse.TagLimit`'s 8 caps vs `UserTagsResponse`'s 5 catalog groups — three divergent tag-category enumerations.

## Duplicate logic
- **Source of the "user profile" overlap group** (see `UserInfo.md`). `UserInfoItem` is the full shape from which `UserInfo`, and conceptually `FollowerUser`/`VisitorUser`/`ProfileMeResponse.UserInfo`, are projections.
- `code`/`msg` envelope shared with the `code`/`msg` family.
- `RelationInfo` (`followers, following, likes, visitors, recentVisitors`) overlaps `ProfileMeResponse` counters and `LikeCountResponse`.

## Dead or unused code
Live — the userinfo upstream response; `toUserInfo()` is the flatten entry point. Many individual nested fields are likely never read by any caller (only `toUserInfo`'s selected fields + `details` pass-through), but they are retained for pass-through completeness.

## Refactoring recommendations
1. Move `toUserInfo()`/`flattenTags()` into a dedicated mapper class (separate data from behavior).
2. Model 0/1 `Integer` flags as `boolean`.
3. Share one tag-category enum across `TagsInfo`, `TagLimit`, `UserTagsData`.
