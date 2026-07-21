# RoomUser.java

`src/main/java/com/jilali/room/dto/RoomUser.java`

## Purpose
A room member/roster entry — identity plus per-room state flags (in room, on mic, raised hand, banned, VIP/level info) and an optional full `UserBase`.

## Responsibilities
Carry a member's identity, room-membership flags, moderation flags, and gamification fields.

## Public API (record fields, 22)
- `@JsonProperty("user_id") long userId`, `String nickname`, `@JsonProperty("head_url") @Nullable String headUrl`, `@Nullable String nationality`, `@JsonProperty("cname") @Nullable String cname`
- Flags: `isInRoom`, `isOnMic`, `isRaiseHand`, `isTurnOnMic`, `isTurnOnCam`, `isBannedComment`, `isBannedMic`, `fgIsActive` (all boolean, snake_case json).
- `int role`, `@JsonProperty("busi_type") int busiType`, `dailyCostCoins`, `giftLevel`, `vipType`, `fgLevel` (int), `@Nullable String fgName`, `@Nullable UserBase base`.
- Compact constructor: null-coalesces `fgName` and `nickname` to `""`.

## Dependencies
- Imports `@JsonProperty`, `@Nullable`, `@Serdeable`, composes `UserBase`.
- Used in `AudienceReconcileResponse`, `ChannelListItem`; nickname read by `RoomsSearchService`. Referenced in `user.dto.RoomUserListResponse`.

## Coupling and cohesion analysis
Moderately cohesive but **large** (22 fields mixing identity + membership + moderation + gamification). Composes `UserBase` yet *also* duplicates `nickname`/`headUrl`/`nationality` at top level — partial composition.

## Code smells
- **Large record / Data Clump**: 22 fields spanning four concerns.
- **Duplicated identity tuple**: `{userId, nickname, headUrl, nationality}` repeats `HostUser`, `ManagerInfo`, `PinnedComment`.
- **Redundant fields**: `nickname`/`headUrl`/`nationality` exist both top-level and inside the nested `base` (`UserBase`) — same data twice.
- **Primitive Obsession**: `role`, `busiType`, `vipType` int codes.

## Technical debt
Top-level identity vs nested `UserBase` overlap invites drift (which is authoritative?).

## Duplicate logic
Identity tuple overlaps `HostUser`/`ManagerInfo`/`PinnedComment`; `{nickname, headUrl, nationality}` also duplicated inside its own `UserBase base`.

## Dead or unused code
None. Compact-constructor defaults are applied on deserialization.

## Refactoring recommendations
- Compose a shared `UserIdentity` for the four repeated fields.
- Decide whether top-level identity or `base` is canonical and drop the other.
- Group the room-state flags into a nested `MicState`/`Moderation` value type.
