# EnrichBatchRequest

`src/main/java/com/jilali/user/dto/EnrichBatchRequest.java` (15 lines)

## Purpose
Request body for `POST /api/users/enrich-batch` — resolves partial user data (nickname, headUrl, nationality) for a set of IDs received over the LiveHub WebSocket without one HTTP call per user.

## Responsibilities
- Carry a non-empty list of user IDs to enrich.

## Public API
- `List<Long> userIds` — `@NotEmpty`. **No `@JsonProperty`** — wire key is the default camelCase `userIds`.

## Dependencies
Depended on by `UserController.enrichBatch` (`@Valid` bound body). No upstream client type (enrichment is done in-process via `gateway::userInfo`).

## Coupling and cohesion analysis
Trivially cohesive single-field record. Low coupling.

## Code smells
- **Primitive Obsession**: raw `List<Long>`.
- **Inconsistent wire naming vs its twin**: uses camelCase `userIds` while `BatchStatusRequest` uses snake_case `user_ids` for the identical field — inconsistent JSON conventions across two sibling batch endpoints.

## Technical debt
Minimal — validation is present.

## Duplicate logic
- **Exact structural duplicate of `BatchStatusRequest`** — both are `record X(@NotEmpty List<Long> userIds)`. Only the `@JsonProperty` differs. Should collapse into one shared request type.

## Dead or unused code
Live — bound by `UserController.enrichBatch`.

## Refactoring recommendations
1. Merge with `BatchStatusRequest` into a single `UserIdBatchRequest`; standardize on one wire key (`user_ids`).
