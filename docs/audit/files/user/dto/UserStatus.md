# UserStatus

`src/main/java/com/jilali/user/dto/UserStatus.java` (33 lines)

## Purpose
Response from `GET /livehub/user/status?user_id={id}` — tells you WHERE a user currently is (not in a room / hosting own room / guest in another room). Verified against 58 captures.

## Responsibilities
- Carry the user's presence/location: status type, room identifiers, host info, and cosmetic fields.

## Public API
- `int userStatusType` (`user_status_type`; 0/1/2), `long userId` (`user_id`), `@Nullable String roomId` (always empty in captures), `@Nullable String roomName`, `@Nullable Long hostId` (BOXED to absorb upstream drift), `@Nullable String hostName`, `@Nullable String hostNationality`, `@Nullable String cname`, `@Nullable String headUrl`, `@Nullable Integer giftLevel`, `boolean blackened`.

## Dependencies
Depended on by `UserController.status` and `JilaliClient`. Cross-referenced by `HostStatus` javadoc.

## Coupling and cohesion analysis
Cohesive presence model. Bare (no envelope). Well-documented, capture-verified.

## Code smells
- **Primitive Obsession**: `userStatusType` int sentinel (0/1/2) instead of an enum; `roomId`/`cname` untyped strings.
- **Dead-but-retained field**: `roomId` is always empty in every capture (documented; kept because upstream sends the key).

## Technical debt
- `userStatusType` sentinel should be an enum (`NotInRoom`/`HostingOwn`/`Guest`).

## Duplicate logic
- **One of the "status trio"** with `HostStatus` and `UserOnlineStatus`. GENUINELY DISTINCT concepts (see `HostStatus.md`):
  - `UserStatus` = a user's presence/room location (rich: room + host info).
  - `UserOnlineStatus` = minimal `{userId, giftLevel, online}` — a batch-friendly projection; `online` is derivable from `userStatusType`. Overlap: `userId`, `giftLevel`.
  - `HostStatus` = the caller's OWN hosting capabilities — no overlap.
- `hostId`/`hostName`/`hostNationality` overlap the host fields carried in the room package's DTOs.

## Dead or unused code
Live — returned by `UserController.status`. (`roomId` is a live-but-always-empty field, not dead code.)

## Refactoring recommendations
1. Model `userStatusType` as an enum.
2. If a shared per-user presence type is introduced, make `UserOnlineStatus` a projection of it.
