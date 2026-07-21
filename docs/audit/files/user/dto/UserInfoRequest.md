# UserInfoRequest

`src/main/java/com/jilali/user/dto/UserInfoRequest.java` (33 lines)

## Purpose
Request payload for the HelloTalk userinfo API (`ht/encbin`) — maps to the upstream groups-based query. Sent internally by the gateway, not bound from any HTTP endpoint.

## Responsibilities
- Carry the requested `groups`, a `source` tag, and `user_ids`.
- Provide a `forUser(long)` factory with the canonical 16-group set used for full profile lookups.

## Public API
- `List<String> groups`, `String source`, `List<Long> user_ids` (raw snake_case field name — no `@JsonProperty`, relies on the field being literally named `user_ids`).
- Compact constructor null-coalesces `groups`/`user_ids` to empty lists.
- `static UserInfoRequest forUser(long userId)` — builds the 16-group full-profile request.

## Dependencies
Depended on by `JilaliGateway` only. Not HTTP-bound.

## Coupling and cohesion analysis
Cohesive upstream request builder. Low coupling; used internally.

## Code smells
- **Inconsistent wire-key style**: uses a raw Java field literally named `user_ids` (snake_case identifier) instead of `@JsonProperty("user_ids") List<Long> userIds` used everywhere else in the package. The one DTO relying on the field name matching the wire key.
- **Magic string list**: the 16 group names are string literals; `source` is a hardcoded `"OtherProfileActivity#null"`.

## Technical debt
- The group set and `source` are magic constants embedded in the DTO.

## Duplicate logic
- None significant — it's the sole request shape for the userinfo upstream call. Conceptually paired with `UserInfoResponse` (request/response for the same endpoint).

## Dead or unused code
Live — used by `JilaliGateway`.

## Refactoring recommendations
1. Use `@JsonProperty("user_ids") List<Long> userIds` for naming consistency with the rest of the package.
2. Promote the group list / `source` to named constants or an enum.
