# FollowRequest

`src/main/java/com/jilali/user/dto/FollowRequest.java` (10 lines)

## Purpose
Request body for `POST /api/profile/follow` → upstream `POST /relation/follow`.

## Responsibilities
- Carry the target user ID and their nickname for a follow action.

## Public API
- `long followUid` — `@JsonProperty("follow_uid")`, primitive (defaults to 0 if absent — no validation).
- `String nickName` — `@JsonProperty("nick_name")`, non-null in type but **not validated** (no `@NotBlank`).

## Dependencies
Depended on by `ProfileController.follow`.

## Coupling and cohesion analysis
Cohesive two-field request. Low coupling.

## Code smells
- **Missing validation**: neither `followUid` (a primitive `long`, silently 0 when omitted) nor `nickName` is validated, unlike the `@NotEmpty`/`@NotBlank` on the batch and room request DTOs.
- **Primitive Obsession**: raw `long` uid.

## Technical debt
- `followUid` as primitive `long` means a missing/zero uid passes straight through to upstream.

## Duplicate logic
- **Near-mirror of `UnfollowRequest`**: `UnfollowRequest(@JsonProperty("unfollow_uid") long unfollowUid, @JsonProperty("nick_name") String nickName)`. The ONLY difference is the uid JSON key (`follow_uid` vs `unfollow_uid`). Both carry `{uid, nick_name}`. Could share a base `RelationRequest(long targetUid, String nickName)` if the wire keys were unified, or the divergent key kept via a mapper.

## Dead or unused code
Live — bound by `ProfileController.follow`.

## Refactoring recommendations
1. Add `@Positive` on the uid (or box it `@NotNull`) and `@NotBlank` on `nickName`.
2. Unify with `UnfollowRequest` if the upstream wire keys can be normalized server-side.
