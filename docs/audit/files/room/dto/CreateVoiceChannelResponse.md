# CreateVoiceChannelResponse.java

`src/main/java/com/jilali/room/dto/CreateVoiceChannelResponse.java`

## Purpose
Response for room creation — returns the new channel identity and RTC token.

## Responsibilities
Carry cname, token, and rtc engine id.

## Public API (record fields)
- `String cname`
- `String token`
- `@JsonProperty("rtc_engine") int rtcEngine`

## Dependencies
- Imports `@JsonProperty`, `@Serdeable`.
- Returned (201) by `RoomController.createVoiceChannel`; produced by `JilaliClient.createVoiceChannel`.

## Coupling and cohesion analysis
Cohesive, minimal.

## Code smells
- Note: `token` here is NOT run through `AgoraTokenCipher` (unlike the room-info path). If the create response token is also AES-encrypted upstream, this is an inconsistency; if it is already plain, fine. Worth confirming.

## Technical debt
Possible missing decrypt on `token` (verify upstream shape).

## Duplicate logic
None.

## Dead or unused code
None.

## Refactoring recommendations
Confirm whether `token` needs the same decrypt treatment as `rtc_info.token`.
