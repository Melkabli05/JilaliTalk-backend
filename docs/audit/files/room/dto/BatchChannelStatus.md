# BatchChannelStatus.java

`src/main/java/com/jilali/room/dto/BatchChannelStatus.java`

## Purpose
Per-channel status element of the batch-query response — reports whether a room is live/ended.

## Responsibilities
Carry a channel's cname, room status, and optional ended timestamp.

## Public API (record fields)
- `String cname` — channel name.
- `@JsonProperty("room_status") int roomStatus` — status code.
- `@JsonProperty("ended_at") @Nullable Long endedAt` — end time, null if active.

## Dependencies
- Imports `@JsonProperty`, `@Nullable`, `@Serdeable`.
- Contained in `BatchQueryResponse.items`; produced upstream via `JilaliClient.batchQueryChannel`.

## Coupling and cohesion analysis
Cohesive, minimal, standalone.

## Code smells
- **Data Class** (acceptable). `roomStatus` is an untyped `int` (magic status codes) — mild **Primitive Obsession** shared across the package (also in `Channel`, `VoiceRoomInfoResponse.ChannelInfo`).

## Technical debt
Room-status int codes are undocumented and duplicated across DTOs; an enum would help.

## Duplicate logic
`cname` + `roomStatus` pair overlaps conceptually with `Channel` (which also has `cname`, `roomStatus`), but this is a deliberately slimmer status-only projection. Minor overlap.

## Dead or unused code
None (framework-serialized).

## Refactoring recommendations
Introduce a shared `RoomStatus` enum used by this, `Channel`, and `ChannelInfo`.
