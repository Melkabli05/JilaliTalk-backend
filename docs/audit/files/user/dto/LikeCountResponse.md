# LikeCountResponse

`src/main/java/com/jilali/user/dto/LikeCountResponse.java` (18 lines)

## Purpose
Response for `GET /api/profile/like-count` — unread profile-like counts. Uses the `status`/`message` envelope.

## Responsibilities
- Wrap the unread favor counters.

## Public API
- `int status`, `String message`, `@Nullable LikeCountData data`.
  - `LikeCountData`: `int unreadFavorCount` (`unread_favor_count`), `int unreadFavorPeople` (`unread_favor_people`).

## Dependencies
Depended on by `ProfileController.likeCount` and `ProfileClient`.

## Coupling and cohesion analysis
Cohesive two-counter envelope. Low coupling.

## Code smells
- Yet another `status`/`message` envelope wrapper (see package-level envelope inconsistency).

## Technical debt
Minimal.

## Duplicate logic
- Envelope shape (`int status, String message, @Nullable Data`) identical to `FollowResultResponse`, `FollowersResponse`, `FollowingResponse`, `ProfileIncrementResponse`, `ProfileStatsResponse`, `UnfollowResultResponse` — the `status`/`message` envelope family. Candidate for a generic `StatusMessageEnvelope<T>`.
- The `unreadFavorCount`/`unreadFavorPeople` counters conceptually overlap `ProfileMeResponse.Increment`'s `newProfileLikeCount`/`newProfileLikePeople` — the same "unread likes" concept surfaced by two different endpoints.

## Dead or unused code
Live — returned by `ProfileController.likeCount`.

## Refactoring recommendations
1. Fold into a shared `StatusMessageEnvelope<LikeCountData>`.
