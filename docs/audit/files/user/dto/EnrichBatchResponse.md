# EnrichBatchResponse

`src/main/java/com/jilali/user/dto/EnrichBatchResponse.java` (15 lines)

## Purpose
Response for `POST /api/users/enrich-batch` — returns all successfully resolved `UserInfo` profiles in one round-trip. Partial failures are silently dropped.

## Responsibilities
- Wrap the list of enriched `UserInfo` records.

## Public API
- `List<UserInfo> profiles` — non-null (not `@Nullable`; the controller always supplies a list).

## Dependencies
Depended on by `UserController.enrichBatch`. Depends on `UserInfo`.

## Coupling and cohesion analysis
Cohesive thin wrapper. Coupling to `UserInfo` (the heavy flattened profile type).

## Code smells
- Thin single-list wrapper — arguably could just return `List<UserInfo>` directly, but a named record keeps the API self-describing.
- Silent partial-failure semantics (documented) hide upstream errors from the client.

## Technical debt
- No indication to the client which requested IDs failed to resolve — the response can't distinguish "not found" from "upstream error."

## Duplicate logic
- Conceptually parallels `BatchStatusResponse` (both "batch → list" responses), but payloads differ (`UserInfo` vs `UserOnlineStatus`). Not a true duplicate.

## Dead or unused code
Live — returned by `UserController.enrichBatch`.

## Refactoring recommendations
1. Consider returning resolved + unresolved-ID sets so the client can react to gaps.
