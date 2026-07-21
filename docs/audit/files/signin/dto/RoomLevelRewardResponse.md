# RoomLevelRewardResponse

## Purpose
Response for `GET /api/signin/room-level-reward` — the list of `RewardItem`s available at a given room level for a host.

## Public API
Record `RoomLevelRewardResponse`:
- `List<RewardItem> items` — the reward entries for the requested level.

## Coupling
Holds a list of `signin.dto.RewardItem`; surfaced by `SigninController.roomLevelReward` and bundled into `RoomLevelBundleResponse`.

## Notes
Wrapper of only one field — `items` — typed as a non-nullable `List<RewardItem>`. Empty upstream lists are passed through as-is by `JilaliResponses.unwrap`.