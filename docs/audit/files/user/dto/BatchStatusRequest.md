# BatchStatusRequest

`src/main/java/com/jilali/user/dto/BatchStatusRequest.java` (11 lines)

## Purpose
Request body for `POST /api/users/status/batch` — asks upstream for the online status of a list of user IDs in one call.

## Responsibilities
- Carry a validated, non-empty list of HelloTalk user IDs to the batch online-status endpoint.

## Public API
- `List<Long> userIds` — JSON key `user_ids` (`@JsonProperty`), `@NotEmpty`. Non-null (validated).

## Dependencies
Depended on by `UserController.batchStatus` (`@Valid` bound body) and declared as the upstream call parameter in `JilaliClient`.

## Coupling and cohesion analysis
Trivially cohesive single-field request record. Coupling low — only the controller and upstream client.

## Code smells
- **Primitive Obsession**: `List<Long>` of raw IDs rather than a typed `UserId`. Endemic to the whole package, not worth fixing per-DTO.

## Technical debt
Minimal. This is one of the few request DTOs that correctly validates its input (`@NotEmpty`).

## Duplicate logic
- **Near-duplicate of `EnrichBatchRequest`** — that record is `record EnrichBatchRequest(@NotEmpty List<Long> userIds)`, structurally IDENTICAL. The only difference: `BatchStatusRequest` adds `@JsonProperty("user_ids")` (snake_case wire key) while `EnrichBatchRequest` relies on the default `userIds` camelCase key. They are the same shape for two different batch endpoints and should share one `UserIdsRequest` base (or be the single type).

## Dead or unused code
Live — bound by `UserController.batchStatus`.

## Refactoring recommendations
1. Merge with `EnrichBatchRequest` into a single `UserIdBatchRequest(@JsonProperty("user_ids") @NotEmpty List<Long> userIds)` used by both `/status/batch` and `/enrich-batch`. Requires aligning the frontend on one wire key.
