# `com.jilali.signin.dto` — daily-check-in & task-reward shapes

## Files (8)

| DTO | Purpose |
|---|---|
| `ClaimRewardRequest` | `{host_id, cname}` — claim body for a daily sign-in reward. |
| `ClaimTaskRewardRequest` | `{host_id, cname, task_id}` — claim body for a voice-task reward (one extra `task_id`). |
| `RewardItem` | One reward line — 8 fields (id, name, multiName, smallPic, bigPic, gift_count, type, status). **EXACT CLONE** of `room/dto.RoomLevelConfigResponse`'s nested `RewardItem`. The only delta: `multi_name` is `@Nullable` in the room copy, non-nullable in signin. |
| `RoomLevelBundleResponse` | `{room: RoomLevelConfigResponse, reward: List<RewardItem>}` — cross-package reuse, wraps a room.dto with a list of signin.dto clones. |
| `RoomLevelRewardResponse` | Single-field wrapper around `List<RewardItem>` — could be inlined. |
| `SignItem` | One day's sign-in record (date, isSigned, bonus, extras). |
| `VoiceSignPanelResponse` | Calendar payload wrapping signInDay/signItem. |
| `VoiceTasksResponse` | Voice-task definitions with `@Nullable` items accessor defaulting to `List.of()`. |

## Cross-package duplication

- `RewardItem` ↔ `room/dto.RoomLevelConfigResponse.RewardItem` — exact 8-field clone. **Consolidate.**

## Improvement opportunities

1. **High**: lift `RewardItem` to a shared `com.jilali.platform.models.reward` (or similar) and have BOTH packages reference the single record.
2. **Low**: `RoomLevelRewardResponse` is a single-field wrapper — probably eligible for inlining once `RewardItem` is consolidated.
