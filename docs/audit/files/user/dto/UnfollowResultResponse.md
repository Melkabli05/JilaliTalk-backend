# UnfollowResultResponse

`src/main/java/com/jilali/user/dto/UnfollowResultResponse.java` (25 lines)

## Purpose
Response from `POST /relation/unfollow`. Verified live: a successful unfollow carries only `list_timestamp` in `data`, unlike `/relation/follow`. Uses `status`/`message` envelope.

## Responsibilities
- Wrap the minimal unfollow result; the controller then normalizes it into `FollowResultResponse`.

## Public API
- `int status`, `String message`, `@Nullable UnfollowResultData data`.
  - `UnfollowResultData`: `long listTimestamp` (`list_timestamp`).

## Dependencies
Depended on by `ProfileController.unfollow` (upstream shape) and `ProfileClient`. References `FollowResultResponse` in javadoc.

## Coupling and cohesion analysis
Cohesive minimal envelope. It exists as a distinct upstream type but the controller immediately maps it into `FollowResultResponse` for the frontend.

## Code smells
- **Data class that is a strict subset** of `FollowResultResponse` — arguably justified (see below) but adds a type.

## Technical debt
- The unfollow→follow-result mapping lives in the controller (Feature Envy), not a factory.

## Duplicate logic
- **Strict subset of `FollowResultResponse`**: `UnfollowResultData` = `{listTimestamp}`; `FollowResultData` = `{listTimestamp, status, limitCount, createTime}`. Both outer envelopes are identical (`int status, String message, @Nullable Data`). The author kept them separate DELIBERATELY (documented): upstream doesn't send follow's extra fields on unfollow, so reusing `FollowResultResponse` would leave three fields perpetually zero/misleading. This is a defensible non-consolidation.

## Dead or unused code
Live — used by `ProfileController.unfollow`.

## Refactoring recommendations
1. Keep the separate deser type, but move the unfollow→follow mapping into a `FollowResultResponse.fromUnfollow(...)` factory to remove it from the controller.
