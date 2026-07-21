# RoomUserListRequest

`src/main/java/com/jilali/user/dto/RoomUserListRequest.java` (16 lines)

## Purpose
Request body for `POST /api/users/rooms/list` — fetch the roster of a room. Also reused by the room package's join flow.

## Responsibilities
- Carry the get-type filter, room name, and business type.

## Public API
- `@Nullable List<Integer> getType` (`get_type`).
- `String cname` — `@NotBlank` (validated).
- `int busiType` (`busi_type`).

## Dependencies
Depended on by `UserController.roomUsers`, `RoomJoinService`, `RoomController`, and `JilaliClient`. Cross-package reuse (room + user).

## Coupling and cohesion analysis
Cohesive room-roster request; genuinely shared across two packages (reuse, not duplication).

## Code smells
- **Primitive Obsession**: `getType` as `List<Integer>` of opaque codes; `cname`+`busiType` untyped pair.

## Technical debt
Minimal — validates `cname`.

## Duplicate logic
- `cname`+`busiType` pair recurs in `HeartbeatRequest`, `RoomUser`, and room-package DTOs — candidate `RoomRef` value object.

## Dead or unused code
Live — used by `UserController.roomUsers` and the room package.

## Refactoring recommendations
1. Extract a shared `RoomRef(String cname, int busiType)`.
2. Replace `getType` integer codes with an enum set if the codes are known.
