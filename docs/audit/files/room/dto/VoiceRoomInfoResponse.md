# VoiceRoomInfoResponse.java

`src/main/java/com/jilali/room/dto/VoiceRoomInfoResponse.java`

## Purpose
The full LiveHub `voice_room_info` (and `live_room_info`) response — the largest room DTO. Models host/req-user/channel/config/level info plus many nested sub-objects, and provides an immutable rebuild to swap in the decrypted RTC token.

## Responsibilities
- Model the complete room-info payload.
- Provide `withRtcToken` to return a copy with the plain (decrypted) Agora token, keeping the record rebuild next to the data.

## Public API
- Top-level record fields (all `@Nullable`): `hostInfo`, `reqUserInfo`, `channelInfo`, `configInfo`, `roomLevelInfo`, `managers:List<ManagerInfo>`, `luckBag`, `roomBackground`, `captionInfo`, `rtcInfoOuter`, `userPaidExposureData`, `quickChatInfo`.
- `VoiceRoomInfoResponse withRtcToken(String token)` — rebuilds `channelInfo.rtcInfo` with the plain token (returns `this` if no rtcInfo).
- Nested records: `RtcInfoOuter(appId, token, engine)`, `HostInfo(userId, base:UserBase, isTeacher, isExpert)`, `ReqUserInfo(...17 fields..., Ripple)`, `ChannelInfo(...27 fields..., RtcInfo, CategoryTopicTag)`, `ConfigInfo(10 bool/int flags)`, `RoomLevelInfo(level + icon variants)`, `ManagerInfo(userId, headUrl, nationality, nickName, userName, shortFullPy, fullPy, stayTime, isOnline)`.

## Dependencies
- Imports the nested types from `VoiceRoomInfoObjects`, plus `UserBase`, `@JsonIgnoreProperties`, `@JsonProperty`, `@Nullable`, `@Serdeable`.
- Consumed by `RoomController` (info endpoints, decrypt), `RoomJoinService` (bundle, decrypt), `JoinBundleResponse`; produced by `JilaliClient.voiceRoomInfo/liveRoomInfo`.

## Coupling and cohesion analysis
Central room-info aggregate; inherently large. `withRtcToken` couples it to the RTC-token-swap concern (acceptable — keeps immutable rebuild with the data). The 27-arg `ChannelInfo` rebuild inside `withRtcToken` (lines 45-58) is fragile positional construction.

## Code smells
- **Large Class**: `ChannelInfo` has 27 fields; response has 12 top-level + many nested.
- **Fragile positional rebuild**: `withRtcToken` (lines 44-62) reconstructs `ChannelInfo` by passing all 27 fields positionally — any field reorder/addition silently breaks it. A `with`-style copy or builder would be safer.
- **Duplicate nested `CategoryTopicTag`**: `ChannelInfo.CategoryTopicTag` (int ids, +icon/selectedIcon) duplicates top-level `room.dto.CategoryTopicTag` (long ids). Same-named, divergent.
- **Duplicate `RtcInfo` shapes**: `RtcInfoOuter(appId, token, engine)` and `ChannelInfo.RtcInfo(appId, token, engine)` are identical records in one file.
- **Primitive Obsession**: pervasive int status/role/type codes.

## Technical debt
- Two `RtcInfo`-shaped records and two `CategoryTopicTag`-shaped records within the room DTO graph.
- Positional `ChannelInfo` rebuild is a maintenance hazard.

## Duplicate logic
- `ChannelInfo.CategoryTopicTag` vs `room.dto.CategoryTopicTag` (overlapping fields, int vs long ids).
- `RtcInfoOuter` vs `ChannelInfo.RtcInfo` (identical).
- `HostInfo`/`ReqUserInfo` both compose `UserBase` (good), but `ManagerInfo` re-declares identity fields instead of composing it.

## Dead or unused code
`withRtcToken` is used by both `RoomController` and `RoomJoinService` decrypt paths. All records framework-serialized. None dead.

## Refactoring recommendations
- Replace `withRtcToken`'s positional rebuild with a nested `ChannelInfo.withRtcInfo(...)` helper (localises the field list).
- Merge `RtcInfoOuter` and `ChannelInfo.RtcInfo` into one shared `RtcInfo`.
- Unify the two `CategoryTopicTag` types.
- Compose a shared identity type into `ManagerInfo`.

## Cross-reference
`VoiceRoomInfoObjects.md`, `AgoraTokenCipher.md`, `RoomController.md`, `RoomJoinService.md`, `dto/CategoryTopicTag.md`, `dto/UserBase.md`.
