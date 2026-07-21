# CreateVoiceChannelRequest.java

`src/main/java/com/jilali/room/dto/CreateVoiceChannelRequest.java`

## Purpose
Inbound body for room creation (`POST /api/rooms/voice`), validated at the controller boundary.

## Responsibilities
Carry and validate room-creation parameters.

## Public API (record fields)
- `@JsonProperty("visible_status") int visibleStatus`
- `@NotBlank String name`
- `@JsonProperty("lang_id") @Positive int langId`
- `@JsonProperty("category_id_v2") @Nullable Long categoryIdV2`
- `@JsonProperty("topic_id_v2") @Nullable Long topicIdV2`
- `@Nullable String notice`
- `@JsonProperty("game_type") @Nullable Integer gameType`

## Dependencies
- Imports `@JsonProperty`, `@Nullable`, `@Serdeable`, `@NotBlank`, `@Positive`.
- Consumed by `RoomController.createVoiceChannel`; forwarded via `JilaliClient.createVoiceChannel`.

## Coupling and cohesion analysis
Cohesive request DTO with proper bean validation.

## Code smells
- **Primitive Obsession**: `visibleStatus`/`gameType` untyped int codes.

## Technical debt
`visibleStatus` unvalidated (no range/enum).

## Duplicate logic
None.

## Dead or unused code
None (bound by `@Body`; validated by Micronaut).

## Refactoring recommendations
Enum for `visibleStatus`/`gameType`.
