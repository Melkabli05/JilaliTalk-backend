# ProfileBundleResponse

`src/main/java/com/jilali/user/dto/ProfileBundleResponse.java` (34 lines)

## Purpose
Aggregate response for `GET /api/profile/{userId}/bundle` — everything a profile page needs in one round trip, assembled concurrently by `ProfileBundleService`.

## Responsibilities
- Compose the guaranteed `userInfo` with four optional sub-payloads whose population depends on self-vs-other.

## Public API
- `UserInfo userInfo` — non-null (bundle fails if this fails).
- `boolean isOwnProfile`.
- `@Nullable ProfileStatsResponse.StatsData stats` — populated only when own profile.
- `@Nullable ProfileLimitationsResponse.LimitationsData limitations` — own profile only.
- `@Nullable PayChatInfoResponse.PayChatInfoData payChatInfo` — other profile only.
- `@Nullable ReminderMomentResponse.ReminderMomentData reminderMoment` — other profile only.

## Dependencies
Depended on by `ProfileController.bundle` and `ProfileBundleService.bundle` (constructs it). Depends on the nested `*Data` types of four other response DTOs plus `UserInfo`.

## Coupling and cohesion analysis
High deliberate coupling: it reaches into the inner `*.Data` records of five sibling DTOs. Cohesion is good (single "profile bundle" concept), but the reach-through to nested records makes it brittle — renaming any inner `Data` record breaks it.

## Code smells
- **Middle Man / positional constructor**: 6 positional args (including two null branches) — argument-transposition risk, flagged in `ProfileBundleService.md`.
- **Inappropriate Intimacy**: depends on nested `*.Data` types rather than top-level DTOs.
- **Nullability-as-mode**: the four `@Nullable` fields encode a self/other mode via null presence — a tagged union or two subtypes would be clearer.

## Technical debt
- The self/other population contract is enforced only in prose (javadoc) and the service, not the type.

## Duplicate logic
- This is the composed "full profile" view; contrast with `ProfileMeResponse` (own-profile bootstrap) and `UserInfo` (the flattened target-user profile). All three represent "a HelloTalk user's profile" at different granularities — `ProfileBundleResponse` embeds `UserInfo` and adds own-vs-other sub-payloads, whereas `ProfileMeResponse` has its OWN nested `UserInfo` mini-record (name collision, subset fields). See `ProfileMeResponse.md` and `UserInfo.md`.

## Dead or unused code
Live — the bundle endpoint's response.

## Refactoring recommendations
1. Replace the positional constructor with a builder or two static factories (`forOwn(...)`, `forOther(...)`).
2. Consider a sealed interface (`OwnBundle` / `OtherBundle`) to make the mode explicit and eliminate the four always-half-null fields.
