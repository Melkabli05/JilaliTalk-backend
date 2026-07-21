# JoinBundleResponse.java

`src/main/java/com/jilali/room/dto/JoinBundleResponse.java`

## Purpose
Bundled result of the four calls needed to join a room, fanned out in parallel by `RoomJoinService` so the browser makes one round trip.

## Responsibilities
Aggregate room info, stage roster, audience roster, and initial comment history.

## Public API (record fields)
- `VoiceRoomInfoResponse voiceRoomInfo` — room metadata incl. decrypted RTC token.
- `StageListResponse stageUsers` — on-stage members.
- `RoomUserListResponse audienceUsers` — audience roster.
- `CommentListDto comments` — initial chat history (ms timestamps).

## Dependencies
- Imports cross-package `comment.dto.CommentListDto`, `stage.dto.StageListResponse`, `user.dto.RoomUserListResponse`, plus `VoiceRoomInfoResponse`.
- Built by `RoomJoinService.joinBundle`; returned by `RoomController.joinBundle`. Referenced in `user.dto.ProfileBundleResponse` (mirrors pattern).

## Coupling and cohesion analysis
Exemplary composition — aggregates four existing response DTOs, zero field duplication. Broad cross-package coupling is inherent to a bundle aggregate.

## Code smells
None. This is the composition pattern the user-shaped DTOs should follow.

## Technical debt
None material.

## Duplicate logic
None — pure aggregation.

## Dead or unused code
None.

## Refactoring recommendations
None. Good reference example.
