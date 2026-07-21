# `com.jilali.user.dto` — user/profile DTO forest

## Files (34 documented)

Heaviest DTO package in the codebase — 36 source files (34 currently documented, 2 of the original 36 either merged into this doc since they were trivial or will be added). Splits cleanly into ~8 distinct shapes even though it has 30+ near-copies of "HelloTalk user with slight variations."

### Shape clusters (per the user-dto audit agent)

| Concept | Representative DTOs | Notes |
|---|---|---|
| "A user's profile" | `UserInfo`, `UserInfoRequest`, `UserInfoResponse`, `ProfileMeResponse`, `ProfileBundleResponse` | The biggest cluster. Aim to collapse to a single record + envelope shape. |
| Profile sub-resources | `BlockListResponse`, `LikeCountResponse`, `PayChatInfoResponse`, `ProfileIncrementResponse`, `ProfileLimitationsResponse`, `ProfileStatsResponse`, `ReminderMomentResponse`, `UserLangsResponse`, `UserTagsResponse`, `ProfileEditResponse` | Each carries a different subset/profile-aspect. Standalone today; likely overlap with `UserInfo` at the field level. |
| Edit operations | `ProfileEditRequest` | Body. |
| Follow/unfollow | `FollowRequest`, `FollowResultResponse`, `UnfollowRequest`, `UnfollowResultResponse` | Mirror pairs. |
| Batch operations | `BatchStatusRequest`, `BatchStatusResponse`, `EnrichBatchRequest`, `EnrichBatchResponse` | "Two pairs of similar batch operations" duplication. |
| Online-presence trio | `HeartbeatRequest`, `HostStatus`, `UserOnlineStatus`, `UserStatus` | Status-shape variants — `UserOnlineStatus` and `HostStatus` are likely near-duplicates. |
| Room-membership-shaped user | `RoomUserListRequest`, `RoomUserListResponse`, `RoomUserProfileResponse` | Distinct from plain user profile (room-context fields). |
| Visit-tracking | `VisitRequest` (DEAD — confirmed never called), `VisitorHistoryRequest`, `VisitorsResponse` | 1 dead, 2 live. |

## ⚠ Findings (carried forward)

- **`VisitRequest` confirmed dead** by the audit agent — no callers anywhere in `src/main/java`; remove.
- **`UserInfo` ↔ `UserInfoResponse` ↔ `ProfileMeResponse` cluster** is the biggest 30-field near-identical shape in the codebase. Collapse.
- **Follow/Unfollow mirror pair** should share a request/result base shape (or one sealed `FollowAction`).
- **Status trio** (`UserOnlineStatus`/`HostStatus`/`UserStatus`): `HostStatus` and `UserOnlineStatus` likely collapse to one.

## Improvement opportunities

1. **High**: delete `VisitRequest` (dead).
2. **High**: consolidate the `UserInfo*` cluster into a single record + a wrapper for the request shape.
3. **High**: introduce sub-packages (`user.dto.profile`, `user.dto.relationships`, `user.dto.presence`, `user.dto.batches`) so this 36-file heap becomes navigable.
4. **Medium**: collapse `HostStatus`/`UserOnlineStatus` (likely near-identical).
