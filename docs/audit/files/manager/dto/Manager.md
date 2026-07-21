# `com.jilali.manager.dto.Manager`

## Purpose
Single row in a `ManagerListResponse`, describing one user that holds (or is a candidate for) a manager/moderator role in a room.

## Public API

Record fields:
- `long userId` — `@JsonProperty("user_id")`; the user's id.
- `String nickname` — display name.
- `@Nullable String headUrl` — `@JsonProperty("head_url")`; avatar URL, nullable.
- `@Nullable String nationality` — country code or label, nullable.
- `int role` — numeric role code (meaning owned by upstream).
- `boolean isInRoom` — `@JsonProperty("is_in_room")`; whether the manager is currently present in the room.
- `@Nullable Long stayTime` — `@JsonProperty("stay_time")`; accumulated presence duration, nullable.

## Coupling
Contained in `ManagerListResponse#managerList`. Returned by `ManagerController.list`. See `docs/audit/files/manager/dto/ManagerListResponse.md`.

## Notes
`role` is an unconstrained `int` — no Bean Validation annotation — so semantic meaning (e.g. host vs moderator vs guest) is encoded upstream.
