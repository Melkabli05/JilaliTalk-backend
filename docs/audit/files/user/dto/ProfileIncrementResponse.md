# ProfileIncrementResponse

`src/main/java/com/jilali/user/dto/ProfileIncrementResponse.java` (21 lines)

## Purpose
Response from `POST /profile/v2/increment` — cheap standalone poll of the unseen-counters payload (new followers/visitors/likes) without re-fetching the whole profile bootstrap. Uses `status`/`message` envelope.

## Responsibilities
- Wrap a `ProfileMeResponse.Increment` payload in a standalone envelope.

## Public API
- `int status`, `String message`, `@Nullable ProfileMeResponse.Increment data`.

## Dependencies
Depended on by `ProfileController.increment` and `ProfileClient`. **Reuses** `ProfileMeResponse.Increment` for `data`.

## Coupling and cohesion analysis
Cohesive. Notable POSITIVE: it reuses `ProfileMeResponse.Increment` instead of redefining the counters — the package's best example of intentional shape reuse.

## Code smells
- Envelope mismatch: this uses `status`/`message` while its sibling `ProfileMeResponse` (which owns `Increment`) uses `code`/`msg` — the SAME data embedded under two different envelopes (documented in the javadoc).

## Technical debt
Minimal.

## Duplicate logic
- The `data` payload is literally shared with `ProfileMeResponse` (good). The `status`/`message` envelope is shared with the whole `status`/`message` family (`LikeCountResponse`, `FollowResultResponse`, etc.).
- `Increment`'s `newProfileLikeCount`/`newProfileLikePeople` overlap `LikeCountResponse`'s `unreadFavorCount`/`unreadFavorPeople` (same "unread likes" concept from two endpoints).

## Dead or unused code
Live — returned by `ProfileController.increment`.

## Refactoring recommendations
1. If a generic envelope is adopted, this becomes `StatusMessageEnvelope<ProfileMeResponse.Increment>`.
