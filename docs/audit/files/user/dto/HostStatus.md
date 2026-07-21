# HostStatus

`src/main/java/com/jilali/user/dto/HostStatus.java` (24 lines)

## Purpose
Response from `GET /livehub/host_status`. Flags about the CALLING user's own account eligibility to go live / host a voice room. Verified against 39 captures.

## Responsibilities
- Carry ten boolean capability/ban flags for the caller's own hosting state.

## Public API
All primitive `boolean`, all `@JsonProperty` snake_case:
- `isLiveHost`, `isVoiceRoomHost`, `isBannedLive`, `isBannedVoiceRoom`, `isBlackHoleUser`, `isHideUser`, `isMinor`, `isApplyLive`, `isApplyVoice`, `isShowTotalRankingList`.

## Dependencies
Depended on by `UserController.hostStatus` and `JilaliClient`. Its javadoc cross-references `UserStatus`.

## Coupling and cohesion analysis
Highly cohesive — a flat flag bag for one concern (own hosting eligibility). Bare response (no envelope).

## Code smells
- **Boolean-heavy flag record (mild)**: ten booleans; a bitset or capability enum-set would be denser but the flat record is clear and matches the wire.

## Technical debt
None notable — well-documented, capture-verified.

## Duplicate logic
- **One of the "status trio"** with `UserStatus` and `UserOnlineStatus`. Despite the shared "status" naming these are genuinely DIFFERENT concepts:
  - `HostStatus` = the CALLER's own hosting capability flags (no `userId` field at all).
  - `UserStatus` = WHERE a given user currently is (presence/room location).
  - `UserOnlineStatus` = a minimal online boolean for a user.
  There is essentially **no field overlap** between `HostStatus` and the other two — this is not duplication, just naming collision. No consolidation warranted.

## Dead or unused code
Live — returned by `UserController.hostStatus`.

## Refactoring recommendations
1. None structurally. Optionally rename to `OwnHostEligibility` to break the "status" naming confusion with `UserStatus`.
