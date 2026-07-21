# UserInfo

`src/main/java/com/jilali/user/dto/UserInfo.java` (63 lines)

## Purpose
The clean, flattened user-profile DTO returned to frontend clients, derived from the encrypted HelloTalk userinfo response. The package's canonical "a HelloTalk user" shape.

## Responsibilities
- Flatten the most-used fields off the heavy `UserInfoResponse.UserInfoItem` while retaining the full `details` for clients that need more.
- Provide `pointsTotal()` (sum of six contribution categories) and `mapSex(int)` label mapping.

## Public API
- `long userId`; `@Nullable String username, nickname, birthday, accountType, fullPy`; `@Nullable Integer age`; `@Nullable String sex` (mapped label), `nationality, city, fullCountry, areaCode`; `@Nullable Integer regDays`; `@Nullable String liveStateCname`; `@Nullable List<String> tags` (pre-flattened); `@Nullable UserInfoResponse.UserInfoItem details` (full upstream profile).
- `int pointsTotal()` — sums six point categories, null-safe.
- `static String mapSex(int)` — 0=female, 1=male, else unknown.

## Dependencies
Depended on by `UserController` (`/info`, enrichment), `EnrichBatchResponse`, `ProfileBundleResponse`, `UserInfoResponse.toUserInfo()` (constructs it), `JilaliGateway`, `ImEventEnricher`, `HtImUpstreamConnector`, `HelloTalkAuthService`, `LoginResponse`. Widely used — the package's most-referenced DTO.

## Coupling and cohesion analysis
Cohesive but carries a dual nature: flattened convenience fields PLUS the entire `details` payload. Coupling to `UserInfoResponse.UserInfoItem` is intentional (it's the flatten source). Broadly depended-on, so a stable, central type.

## Code smells
- **Primitive Obsession**: `sex` as pre-mapped `String`, `age`/`regDays` as raw `Integer`, timestamps elsewhere.
- **Fat DTO / redundant data**: exposes both flattened fields AND `details` (which contains the same data un-flattened) — the same values are serialized twice on the wire.
- **Name collision**: shares the simple name `UserInfo` with the nested `ProfileMeResponse.UserInfo` (a different 7-field type).

## Technical debt
- Double-serialization of flattened + `details` bloats the payload.
- Behavior (`pointsTotal`, `mapSex`) on a DTO blurs the data/logic boundary (though it deduplicates arithmetic with the frontend, per the javadoc).

## Duplicate logic
- **Central node of the "user profile" overlap group**. Shared/overlapping fields with:
  - `ProfileMeResponse.UserInfo` (nested): `userId, nickname/nickName, nationality, sex` — a 7-field own-profile projection of the same concept.
  - `FollowersResponse/FollowingResponse.FollowerUser`: `userId, nickName, headUrl, nationality, sex, nativeLang, vipType, giftLevel` — a follower mini-profile.
  - `VisitorsResponse.VisitorUser`: `userId, username, nickname, nationality, headUrl, birthday, sex, nativeLang, giftLevel`.
  - `room.dto.RoomUser`: `userId, nickname, headUrl, nationality, giftLevel, vipType`.
  All are projections of one HelloTalk user. A shared `UserProfileCore(userId, nickname, headUrl, nationality, sex, ...)` base could underpin them.

## Dead or unused code
Live — heavily used. `pointsTotal()`/`mapSex()` both referenced (mapSex used by `UserInfoResponse.toUserInfo`).

## Refactoring recommendations
1. Decide whether the wire needs both flattened fields AND `details` — drop one to halve the payload.
2. Extract a `UserProfileCore` mini-profile base shared by `FollowerUser`, `VisitorUser`, and `ProfileMeResponse.UserInfo`.
3. Rename the nested `ProfileMeResponse.UserInfo` to end the name collision.
