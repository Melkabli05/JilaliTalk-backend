# EndChannelRequest.java

`src/main/java/com/jilali/room/dto/EndChannelRequest.java`

## Purpose
Inbound body for ending a room (`POST /api/rooms/end`).

## Responsibilities
Carry the cname to end and the ended-type reason code.

## Public API (record fields)
- `@NotBlank String cname`
- `@JsonProperty("ended_type") int endedType`

## Dependencies
- Imports `@JsonProperty`, `@Serdeable`, `@NotBlank`.
- Consumed by `RoomController.endChannel`; forwarded via `JilaliClient.endChannel`.

## Coupling and cohesion analysis
Cohesive, minimal request DTO.

## Code smells
- **Primitive Obsession**: `endedType` untyped int code.

## Technical debt
`endedType` unvalidated / undocumented codes.

## Duplicate logic
None.

## Dead or unused code
None.

## Refactoring recommendations
Enum for `endedType`.
