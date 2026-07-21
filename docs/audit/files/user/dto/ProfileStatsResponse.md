# ProfileStatsResponse

`src/main/java/com/jilali/user/dto/ProfileStatsResponse.java` (25 lines)

## Purpose
Response from `POST /profile/v1/baseinfo/mnt_info` — the caller's own Moment stats. Uses `status`/`message` envelope.

## Responsibilities
- Wrap Moment-count / like-count / registration timestamps.

## Public API
- `int status`, `String message`, `@Nullable StatsData data`.
  - `StatsData`: `int totalMntCount` (`total_mnt_count`), `int totalLikeCount`, `int lastMntLikeCount`, `long lastMntPostTs`, `long registeredTs`.

## Dependencies
Depended on by `ProfileController.stats`, `ProfileBundleService`, `ProfileBundleResponse` (embeds `StatsData`), and `ProfileClient`.

## Coupling and cohesion analysis
Cohesive stats envelope. `StatsData` is reached into by `ProfileBundleResponse`.

## Code smells
- `status`/`message` envelope duplication.
- **Primitive Obsession**: epoch timestamps as raw `long` (no `Instant`), consistent with the rest of the package.

## Technical debt
Minimal — capture-informed.

## Duplicate logic
- Envelope shared with the `status`/`message` family (`LikeCountResponse`, `FollowResultResponse`, etc.).
- `registeredTs` overlaps `UserInfoResponse.BaseInfo.regTime`/`regDays` and `UserInfo.regDays` — registration data surfaced by multiple profile DTOs.

## Dead or unused code
Live — used by controller and bundle.

## Refactoring recommendations
1. Fold into `StatusMessageEnvelope<StatsData>`.
2. Represent timestamps as a shared epoch-ms type if the package ever standardizes.
