# RoomUserListResponse

`src/main/java/com/jilali/user/dto/RoomUserListResponse.java` (18 lines)

## Purpose
Response for `POST /api/users/rooms/list` — the room roster plus an audience total. Bare (envelope-less).

## Responsibilities
- Wrap a null-coalesced list of `RoomUser` and the audience count.

## Public API
- `List<RoomUser> list` — `@Nullable`; accessor overridden to return `List.of()` when null.
- `int audienceTotal` — `@JsonProperty("audience_total")`.

## Dependencies
Depended on by `UserController.roomUsers`, `RoomJoinService`, `JoinBundleResponse` (room package embeds it), and `JilaliClient`. Depends on `com.jilali.room.dto.RoomUser`.

## Coupling and cohesion analysis
Cohesive roster wrapper. Cross-package coupling to `room.dto.RoomUser` (the canonical room-user shape lives in the room package, not here).

## Code smells
- **Package placement**: a `RoomUser*`-centric response living in `user.dto` while the `RoomUser` model lives in `room.dto` — split domain ownership.

## Technical debt
- The null-coalescing `list()` idiom is duplicated (see `BatchStatusResponse`, `RoomUser`).

## Duplicate logic
- `list()` null-coalescing accessor duplicated with `BatchStatusResponse.others()`.
- Conceptually related to `RoomUserProfileResponse` only by name — that type is a per-user follow-relation lookup, NOT a roster. No field overlap; different concerns despite the shared `RoomUser` prefix.

## Dead or unused code
Live — used across user + room packages.

## Refactoring recommendations
1. Consider moving this response to `room.dto` alongside `RoomUser` for domain cohesion.
2. Standardize the null-coalescing list idiom.
