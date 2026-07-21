# SigninController

## Purpose
Micronaut REST controller exposing the `/api/signin` endpoints — sign-in panel, voice tasks, room-level rewards, room-level config, and the bundled `room-level-bundle` that fans out the reward + config calls concurrently.

## Public API
- `SigninController(JilaliClient client)` — constructor injection of the upstream client.
- `VoiceSignPanelResponse panel(String cname)` — `GET /panel`; returns the voice sign-in panel for the given cname.
- `VoiceTasksResponse tasks()` — `GET /tasks`; returns voice task list (typed as `List<Map<String,Object>>`).
- `RoomLevelRewardResponse roomLevelReward(String cname, long hostId, int level)` — `GET /room-level-reward`; rewards for a host at a given level.
- `HttpResponse<Void> claimRoomLevelReward(@Valid ClaimRewardRequest)` — `POST /room-level-reward`; claim the level reward.
- `HttpResponse<Void> claimTaskReward(@Valid ClaimTaskRewardRequest)` — `POST /task-reward`; claim a voice-task reward.
- `RoomLevelConfigResponse roomLevelConfig(String cname, long hostId)` — `GET /room-level-config`; level-config items.
- `RoomLevelBundleResponse roomLevelBundle(String cname, long hostId, int level)` — `GET /room-level-bundle`; concurrent fan-out of reward + config via `StructuredTaskScope`.

## Coupling
Depends on `JilaliClient` (upstream REST client), `JilaliResponses` (response unwrapping helpers), and the `com.jilali.signin.dto.*` record types. Runs on `TaskExecutors.BLOCKING`.

## Notes
The `room-level-bundle` endpoint explicitly mirrors the `RoomJoinService.joinBundle` pattern — same `StructuredTaskScope` fan-out for two-calls-as-one. Both `claimRoomLevelReward` and `claimTaskReward` return `204 No Content` on success.