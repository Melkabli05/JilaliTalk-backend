# HostUser.java

`src/main/java/com/jilali/room/dto/HostUser.java`

## Purpose
Flat host-user block on a `ChannelListItem`, deserialized directly from upstream `host_user`.

## Responsibilities
Carry the host's identity: id, nickname, avatar, nationality.

## Public API (record fields)
- `@JsonProperty("user_id") long userId`
- `@Nullable String nickname`
- `@JsonProperty("head_url") @Nullable String headUrl`
- `@Nullable String nationality`

## Dependencies
- Imports `@JsonProperty`, `@Nullable`, `@Serdeable`.
- Contained in `ChannelListItem`; nickname read by `RoomsSearchService.matchesQuery`.

## Coupling and cohesion analysis
Cohesive, tiny identity DTO.

## Code smells
- **Data Class**.
- **Duplicated identity tuple**: `{userId, nickname, headUrl, nationality}` is byte-for-byte the same shape as the leading fields of `RoomUser`, `VoiceRoomInfoResponse.ManagerInfo`, and `VoiceRoomInfoObjects.PinnedComment`. No shared base type — field duplication instead of composition.

## Technical debt
This package has no shared "user identity" base; the same four fields recur across ≥4 DTOs.

## Duplicate logic
Identity tuple `{userId, nickname, headUrl, nationality}` overlaps `RoomUser`, `ManagerInfo`, `PinnedComment`. `UserBase` holds the `{nickname, headUrl, nationality}` subset but is not composed here.

## Dead or unused code
None.

## Refactoring recommendations
Extract a shared `UserIdentity(userId, nickname, headUrl, nationality)` and compose it in `HostUser`, `RoomUser`, `ManagerInfo`, `PinnedComment`.
