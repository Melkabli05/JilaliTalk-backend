# BatchStatusResponse

`src/main/java/com/jilali/user/dto/BatchStatusResponse.java` (16 lines)

## Purpose
Response for `POST /api/users/status/batch` — online status of the requested users, split into a primary `list` and an `others` bucket.

## Responsibilities
- Wrap two nullable lists of `UserOnlineStatus`.
- Null-coalesce `others()` to an empty list via an accessor override.

## Public API
- `List<UserOnlineStatus> list` — `@Nullable` (raw accessor can return null).
- `List<UserOnlineStatus> others` — `@Nullable`; overridden accessor returns `List.of()` when null.

## Dependencies
Depended on by `UserController.batchStatus` and declared in `JilaliClient` as the upstream return type.

## Coupling and cohesion analysis
Cohesive; depends only on the leaf `UserOnlineStatus`. This is a bare (envelope-less) response — no `code`/`msg`.

## Code smells
- **Inconsistent null handling**: `others()` is null-coalesced but `list()` is NOT — a caller of `list()` can still NPE while `others()` is safe. Asymmetric defensive coding.
- Unclear semantics: what distinguishes `list` from `others` is undocumented (no javadoc).

## Technical debt
- Missing `list()` null-coalescing override to match `others()`.
- No javadoc explaining the `list` vs `others` split.

## Duplicate logic
- The null-coalescing accessor pattern (`return x == null ? List.of() : x`) is duplicated in `RoomUserListResponse.list()` and `RoomUser`'s compact constructor — a recurring idiom worth a shared helper or consistent convention.
- Conceptually parallels `EnrichBatchResponse` (both "batch response wrapping a list"), though payload types differ (`UserOnlineStatus` vs `UserInfo`).

## Dead or unused code
Live — returned by `UserController.batchStatus`.

## Refactoring recommendations
1. Add the same null-coalescing override for `list()`.
2. Document the `list`/`others` distinction.
