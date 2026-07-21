# DeviceControlRequest

`src/main/java/com/jilali/stage/dto/DeviceControlRequest.java`

## Purpose
Request body for `POST /device-control` — toggling a speaker's mic/cam on or off.

## Responsibilities
Transport `cname`, `switch_type`, `busi_type`, `device_type` to `JilaliGateway.deviceControl`.

## Public API
Record `DeviceControlRequest`:
- `String cname` — `@NotBlank`.
- `int switchType` (`@JsonProperty("switch_type")`) — on/off discriminator.
- `int busiType` (`@JsonProperty("busi_type")`).
- `int deviceType` (`@JsonProperty("device_type")`) — mic vs cam discriminator.

## Dependencies
- `@Serdeable`, `@JsonProperty`, `@NotBlank`.
- Depended on by: `StageController.deviceControl`, `JilaliGateway`, `JilaliClient`.

## Coupling and cohesion analysis
Cohesive; = `StageActionRequest` base + two device discriminators. No `user_id`, so it targets the caller's own device (self-service, not moderation).

## Code smells
- **Data Class**; **Primitive Obsession** — `switchType` (boolean-in-disguise) and `deviceType` (enum-in-disguise) are both bare ints.
- Member of the duplicated stage-action DTO family.

## Technical debt
`switchType` is effectively a boolean encoded as int; `deviceType` is an enum encoded as int. Undocumented value spaces.

## Duplicate logic
`{cname, busiType}` base shared with all siblings; adds `switchType`+`deviceType`. See package overlap table.

## Dead or unused code
None.

## Java 25 modernization opportunities
`record DeviceControl(...) implements StageMemberAction`; `switchType` → `boolean` or `SwitchState` enum, `deviceType` → `DeviceType` enum.

## Micronaut built-in opportunities
`@Min/@Max` on `deviceType`/`switchType`; consider a `boolean` for switchType if the upstream int is 0/1.

## Refactoring recommendations
Consolidate; replace `switchType` with `boolean`, `deviceType` with an enum.
