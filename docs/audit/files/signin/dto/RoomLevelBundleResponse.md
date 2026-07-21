# RoomLevelBundleResponse

## Purpose
Combined response for `GET /api/signin/room-level-bundle` — packages the room-level reward list and the room-level config into one payload so the frontend's rewards tab avoids a second round trip.

## Public API
Record `RoomLevelBundleResponse`:
- `RoomLevelRewardResponse reward` — reward-list half of the bundle.
- `RoomLevelConfigResponse config` — level-config half of the bundle (type reused from `com.jilali.room.dto`).

## Coupling
Composed of `RoomLevelRewardResponse` (this package) and `RoomLevelConfigResponse` (cross-package reuse from `room.dto`).

## Notes
Server-side fan-out is performed by `SigninController.roomLevelBundle` using `StructuredTaskScope.open()`; the docs intentionally mirror the `RoomJoinService.joinBundle` pattern.