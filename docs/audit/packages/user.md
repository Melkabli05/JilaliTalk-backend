# `com.jilali.user` — HelloTalk user/profile lookups

## Purpose

Lookups of OTHER HelloTalk users (someone else's profile, follower/following list, etc.) on behalf of the Angular frontend. NOT to be confused with `auth` (which manages the browser's own authentication) — this package is downstream of it.

## File responsibilities (3 root + 36 dto = 37 documented files, 2 still missing as of audit)

### Root

| File | One-line summary |
|---|---|
| `ProfileBundleService.java` | Fan-out service that aggregates multiple upstream profile calls into a single bundle response — uses Micronaut's `@Cacheable("user-info")`. |
| `ProfileController.java` | The `/api/profile/*` controller (own profile). |
| `UserController.java` | The `/api/user/*` controller (other-user lookups). |

### DTOs (36) — heaviest overlap in the codebase

Per the user-dto audit agent, the 36 DTOs collapse to maybe 8-10 distinct shapes. Specific findings:

| DTO | Represents | Likely relation |
|---|---|---|
| `UserInfo`, `UserInfoRequest`, `UserInfoResponse`, `ProfileMeResponse`, `ProfileBundleResponse` | "A HelloTalk user's profile, possibly in different shapes" | Likely collapse to a single record with a wrapper for the request shape. |
| `BlockListResponse`, `FollowersResponse`, `FollowingResponse`, `LikeCountResponse`, `PayChatInfoResponse`, `ProfileEditResponse`, `ProfileIncrementResponse`, `ProfileLimitationsResponse`, `ProfileStatsResponse`, `ReminderMomentResponse` | Each a profile-related response carrying a slightly different slice | Standalone but field-overlapping with `UserInfo`. |
| `BatchStatusRequest`, `BatchStatusResponse`, `EnrichBatchRequest`, `EnrichBatchResponse` | Batch-user operations | Separate but likely consolidate. |
| `RoomUserListRequest`, `RoomUserListResponse`, `RoomUserProfileResponse` | "A user as seen in a room context" | Distinct from the plain profile shape — likely `RoomUser`-shape. |
| `FollowRequest`, `FollowResultResponse`, `UnfollowRequest`, `UnfollowResultResponse` | Follow/unfollow pair | Mirror pairs that could share a request/result base. |
| `HeartbeatRequest`, `HostStatus`, `UserOnlineStatus`, `UserStatus` | User-presence/heartbeat | Three status-shape types — `UserOnlineStatus` and `HostStatus` and `UserStatus` likely overlap. |
| `VisitRequest`, `VisitorHistoryRequest`, `VisitorsResponse`, `UserLangsResponse`, `UserTagsResponse` | Profile-visit history and tag/language metadata. **`VisitRequest` is fully dead** (per the partial-completion audit agent — independently confirmed, no callers found). |
| `FollowersResponse`, `FollowingResponse` | Paged follow-list responses. |

## Dependencies

- **Inbound**: Angular frontend uses `/api/profile/*` and `/api/user/*`.
- **Outbound**: depends on `client` (`JilaliClient`, `JilaliGateway`, `ProfileClient`), `core`, `crypto` (for encrypted-upstream fields), `room` (RoomUser DTOs re-imported here).

## Improvement opportunities

1. **High — DTO proliferation**: this is the worst pack in the codebase for DTO count vs distinct shape count. A target-architecture rewrite should establish a single `User` record + ~5 derived variants, with mapping boundaries at the feature interface level.
2. **Medium — dead code**: `VisitRequest` confirmed fully dead — remove.
3. **Medium — auth/presence overlap**: `AuthSession`-style status info (online/status) sits oddly in this package along with profile-shape DTOs. Consider splitting into `user.profile` + `user.presence` sub-packages.
4. **Low**: `BatchStatusRequest`/`Response` vs `EnrichBatchRequest`/`Response` — these two pairs likely represent similar batch operations in different forms. Investigate upstream-call duplication.
