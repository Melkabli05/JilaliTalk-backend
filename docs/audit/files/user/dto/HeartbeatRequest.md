# HeartbeatRequest

`src/main/java/com/jilali/user/dto/HeartbeatRequest.java` (13 lines)

## Purpose
Request body for `POST /api/users/rooms/{cname}/heartbeat` — periodic keep-alive telling upstream the caller is still in a room.

## Responsibilities
- Carry host id, foreground-member flag, business type, and room name for the heartbeat.

## Public API
- `long hostId` — `@JsonProperty("host_id")`, primitive.
- `boolean isFgMember` — `@JsonProperty("is_fg_member")`.
- `int busiType` — `@JsonProperty("busi_type")`.
- `String cname` — `@NotBlank` (validated).

## Dependencies
Depended on by `UserController.heartbeat` (`@Valid`) and `JilaliClient`.

## Coupling and cohesion analysis
Cohesive room-lifecycle request. Low coupling.

## Code smells
- **Primitive Obsession**: `busiType` int and `cname` string appear across many room DTOs without a shared type.

## Technical debt
Minimal — validates `cname`.

## Duplicate logic
- `cname` + `busiType` pairing recurs across `RoomUserListRequest`, `RoomUser`, and the room package's request DTOs — a candidate `RoomRef(cname, busiType)` value object.

## Dead or unused code
Live — bound by `UserController.heartbeat`.

## Refactoring recommendations
1. Consider a shared `RoomRef(String cname, int busiType)` value object reused across room-scoped requests.
