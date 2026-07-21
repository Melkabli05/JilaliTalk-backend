# UserOnlineStatus

`src/main/java/com/jilali/user/dto/UserOnlineStatus.java` (13 lines)

## Purpose
Minimal per-user online-state item, carried inside `BatchStatusResponse` for the batch online-status endpoint.

## Responsibilities
- Carry a user's ID, gift level, and a plain online boolean.

## Public API
- `long userId` (`user_id`), `@Nullable Integer giftLevel` (`gift_level`), `boolean online`.

## Dependencies
Depended on by `BatchStatusResponse` (embeds `List<UserOnlineStatus>`). Not referenced elsewhere.

## Coupling and cohesion analysis
Highly cohesive, minimal. Only consumer is `BatchStatusResponse`.

## Code smells
- **Primitive Obsession**: raw `long userId`.

## Technical debt
None notable.

## Duplicate logic
- **One of the "status trio"** with `HostStatus` and `UserStatus`. This is the SMALLEST: just `{userId, giftLevel, online}`.
  - Overlaps `UserStatus` on `userId` + `giftLevel` (and `UserStatus` also conveys presence, so `online` is derivable from `UserStatus.userStatusType`). `UserOnlineStatus` is essentially a stripped batch-friendly projection of the presence concept `UserStatus` carries per-user.
  - No overlap with `HostStatus` (that's the caller's own capability flags, no userId).
  `giftLevel` also recurs across `RoomUser`, `FollowerUser`, `VisitorUser`, `UserStatus` — an ambient per-user field.

## Dead or unused code
Live — nested in `BatchStatusResponse`.

## Refactoring recommendations
1. Could be viewed as a projection of `UserStatus`; if a shared per-user presence type is introduced, derive both from it.
