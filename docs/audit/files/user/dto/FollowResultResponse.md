# FollowResultResponse

`src/main/java/com/jilali/user/dto/FollowResultResponse.java` (24 lines)

## Purpose
Response from `POST /relation/follow`. Uses the `status`/`message` envelope with a nested `data`. Also reused as the normalized shape for `/unfollow` (the controller maps the divergent unfollow response into this type).

## Responsibilities
- Wrap the follow-result envelope and its `data` payload.

## Public API
- `int status` — primitive.
- `String message` — non-null.
- `FollowResultData data` — `@Nullable`.
  - `long listTimestamp` (`list_timestamp`), `int status`, `int limitCount`, `long createTime` — all primitive.

## Dependencies
Depended on by `ProfileController.follow` and `ProfileController.unfollow` (which rebuilds one from an `UnfollowResultResponse`), `ProfileClient`, and referenced in `UnfollowResultResponse`'s javadoc.

## Coupling and cohesion analysis
Cohesive envelope. Note the nested `status` field shadows the outer `status` — two different `status` meanings in one type (envelope status vs relation status), a readability hazard.

## Code smells
- **Ambiguous naming**: both the envelope and `FollowResultData` have a field named `status` with different meanings.
- **`status`/`message` vs `code`/`msg` envelope split** across the package (see package doc).

## Technical debt
- The controller performs the unfollow→follow-result mapping inline (Feature Envy noted in `ProfileController.md`) instead of a factory here.

## Duplicate logic
- **Superset of `UnfollowResultResponse`**: `UnfollowResultData` carries only `listTimestamp`; `FollowResultData` carries `listTimestamp` + `status` + `limitCount` + `createTime`. Unfollow's data is a strict subset. The two response envelopes are otherwise identical (`int status, String message, @Nullable Data`). The author kept them separate deliberately (deser safety — upstream doesn't send the extra fields on unfollow); documented in `UnfollowResultResponse`.

## Dead or unused code
Live — returned by `follow` and constructed in `unfollow`.

## Refactoring recommendations
1. Add a `static FollowResultResponse fromUnfollow(UnfollowResultResponse)` factory to move mapping out of the controller.
2. Rename the nested `status` (e.g. `relationStatus`) to disambiguate from the envelope `status`.
