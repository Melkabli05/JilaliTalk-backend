# UnfollowRequest

`src/main/java/com/jilali/user/dto/UnfollowRequest.java` (10 lines)

## Purpose
Request body for `POST /api/profile/unfollow` → upstream `POST /relation/unfollow`.

## Responsibilities
- Carry the target user ID and nickname for an unfollow action.

## Public API
- `long unfollowUid` — `@JsonProperty("unfollow_uid")`, primitive.
- `String nickName` — `@JsonProperty("nick_name")`, non-null in type, **not validated**.

## Dependencies
Depended on by `ProfileController.unfollow`.

## Coupling and cohesion analysis
Cohesive two-field request. Low coupling.

## Code smells
- **Missing validation** (same as `FollowRequest`): raw `long` uid, unvalidated `nickName`.
- **Primitive Obsession**: raw `long` uid.

## Technical debt
- Zero/absent uid passes through silently.

## Duplicate logic
- **Near-mirror of `FollowRequest`**: both are `record X(@JsonProperty("<x>follow_uid") long uid, @JsonProperty("nick_name") String nickName)`. The ONLY difference is the uid JSON key (`unfollow_uid` vs `follow_uid`). Two records for the same `{uid, nickName}` shape.

## Dead or unused code
Live — bound by `ProfileController.unfollow`.

## Refactoring recommendations
1. Add `@Positive`/`@NotBlank` validation to match sibling request DTOs.
2. Unify with `FollowRequest` into a `RelationRequest(long targetUid, String nickName)` and map the divergent wire key at the client boundary.
