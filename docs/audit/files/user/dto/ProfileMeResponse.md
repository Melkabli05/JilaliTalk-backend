# ProfileMeResponse

`src/main/java/com/jilali/user/dto/ProfileMeResponse.java` (57 lines)

## Purpose
Response from `GET /profile/v2/me` — the OWN-account profile bootstrap. Uses `code`/`msg` envelope (LiveHub format).

## Responsibilities
- Wrap the caller's own user mini-profile, unseen-counter increments, and recent-visitor summary.

## Public API
- `int code`, `String msg`, `@Nullable ProfileMeData data`.
  - `ProfileMeData`: `@Nullable UserInfo user`, `@Nullable Increment increment`, `@Nullable VisitorData visitor`, `boolean isRealAuth`.
  - **`UserInfo` (nested)**: `long userId`, `@Nullable String nickName` (`nick_name`), `@Nullable String headUrl` (`head_url`), `@Nullable String nationality`, `int vipType`, `int sex`, `@Nullable String email`. **This is a DIFFERENT type from the top-level `com.jilali.user.dto.UserInfo` despite the identical name.**
  - `Increment`: `int newFollowerCount`, `int newVisitorCount`, `int newProfileLikeCount`, `int newProfileLikePeople`, `@Nullable List<VisitorInfo> newVisitorInfos`.
  - `VisitorData`: `@Nullable List<VisitorInfo> recentVisitors`.
  - `VisitorInfo`: `long userId`, `@Nullable String headUrl`, `@Nullable String nationality`.

## Dependencies
Depended on by `ProfileController.me`, `ProfileClient`, and `ProfileIncrementResponse` (reuses `Increment`).

## Coupling and cohesion analysis
Cohesive own-profile bootstrap. `Increment` is deliberately reused elsewhere (good).

## Code smells
- **Name collision (significant)**: the nested `ProfileMeResponse.UserInfo` shadows the top-level `UserInfo` DTO. Two records named `UserInfo` in the same package is a readability/maintenance trap.
- **Primitive Obsession**: `sex` as raw `int` here vs `String` (mapped) in the top-level `UserInfo`.

## Technical debt
- Two `UserInfo` shapes for "own user" (7 fields) vs "target user" (16 fields, flattened) with no shared base.

## Duplicate logic
- **`ProfileMeResponse.UserInfo` (nested) overlaps the top-level `UserInfo`**: shared fields `userId`, `nickName`/`nickname`, `nationality`, `sex`, `vipType`(in details). It's a small own-profile projection of the same "user profile" concept.
- **`VisitorInfo` (`userId, headUrl, nationality`) is a subset of `VisitorsResponse.VisitorUser`** (which adds username, nickname, sex, visitTs, etc.) — two visitor representations for the same domain.
- `Increment` counters overlap `LikeCountResponse` (likes) as noted there.

## Dead or unused code
Live — returned by `ProfileController.me`; `Increment` reused by `ProfileIncrementResponse`.

## Refactoring recommendations
1. Rename the nested `UserInfo` (e.g. `MeUser`) to end the name collision, or project from the top-level `UserInfo`.
2. Unify `VisitorInfo` and `VisitorsResponse.VisitorUser` under one visitor type (full + summary via nullability).
