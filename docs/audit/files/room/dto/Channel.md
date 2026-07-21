# Channel.java

`src/main/java/com/jilali/room/dto/Channel.java`

## Purpose
Flat room-metadata block embedded in each `ChannelListItem` (the listing/discovery projection of a room).

## Responsibilities
Carry the summary room fields a listing renders: identity, type, name/description, language, status, counts, heat.

## Public API (record fields)
- `String cname`
- `@JsonProperty("busi_type") int busiType`
- `String name`
- `@Nullable String description`
- `@JsonProperty("lang_id") int langId`
- `@Nullable List<Integer> langs`
- `@JsonProperty("room_status") int roomStatus`
- `@JsonProperty("total_user_count") int totalUserCount`
- `@JsonProperty("heat_value") @Nullable Integer heatValue`

## Dependencies
- Imports `@JsonProperty`, `@Nullable`, `@Serdeable`.
- Contained in `ChannelListItem`; read by `RoomsSearchService.matchesQuery`.

## Coupling and cohesion analysis
Cohesive summary record. Coupled into the search haystack extraction.

## Code smells
- **Primitive Obsession**: `busiType`, `roomStatus` untyped int codes (shared package-wide).
- **Overlap** with `VoiceRoomInfoResponse.ChannelInfo` (the full room-info counterpart) — both model a room but at different granularity.

## Technical debt
`busiType`/`roomStatus`/`langId` magic ints; `langs` as `List<Integer>` while `ChannelInfo.langs` is `int[]` — inconsistent representation of the same concept.

## Duplicate logic
Overlapping fields with `VoiceRoomInfoResponse.ChannelInfo`: `name`, `langId`, `langs`, `roomStatus`. `cname`/`roomStatus` also overlap `BatchChannelStatus`.

## Dead or unused code
None.

## Refactoring recommendations
Introduce shared `RoomStatus`/`BusiType` enums; standardise `langs` representation (`List<Integer>` vs `int[]`) across `Channel` and `ChannelInfo`.
