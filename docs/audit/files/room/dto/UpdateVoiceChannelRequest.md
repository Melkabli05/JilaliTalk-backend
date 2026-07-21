# UpdateVoiceChannelRequest.java

`src/main/java/com/jilali/room/dto/UpdateVoiceChannelRequest.java`

## Purpose
Inbound body for updating a room's managers/types (`POST /api/rooms/voice/update`).

## Responsibilities
Carry cname plus optional manager uid list and type list.

## Public API (record fields)
- `@NotBlank String cname`
- `@JsonProperty("manager_uids") @Nullable List<Long> managerUids`
- `@Nullable List<Integer> types`

## Dependencies
- Imports `@JsonProperty`, `@Nullable`, `@Serdeable`, `@NotBlank`.
- Consumed by `RoomController.updateVoiceChannel`; forwarded via `JilaliClient.updateVoiceChannel`.

## Coupling and cohesion analysis
Cohesive request DTO.

## Code smells
- **Primitive Obsession**: `types` as `List<Integer>` untyped codes.
- Vague field (`types`) with no documentation of meaning.

## Technical debt
Undocumented `types` semantics.

## Duplicate logic
None.

## Dead or unused code
None.

## Refactoring recommendations
Document/enum `types`.
