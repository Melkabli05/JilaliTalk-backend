# `com.jilali.signin` + `signin.dto` — daily-check-in reward feature

## Purpose

HelloTalk's "daily check-in" / voice-task reward feature. The user signs in daily, earns task rewards, claims them — a small promo/engagement feature distinct from `auth` (which handles real login/signup).

## File responsibilities (1 root + 8 dto = 9 files)

### Root

| File | One-line summary |
|---|---|
| `SigninController.java` | `/api/signin/*` with 8 endpoints. The notable one: `roomLevelBundle` uses Java 25 preview `StructuredTaskScope` to fan out — already a modern-Java pattern. |

### DTOs (8)

| DTO | Purpose |
|---|---|
| `ClaimRewardRequest`, `ClaimTaskRewardRequest` | Mark-reward and mark-task-reward claim bodies — both `{host_id, cname[, task_id]}`. |
| `RewardItem` | One reward line — 8 fields. **Cross-package duplicate alert**: an exact 8-field clone of the nested `RewardItem` inside `room/dto.RoomLevelConfigResponse`. |
| `RoomLevelBundleResponse` | Wraps `RoomLevelRewardResponse` + `room.dto.RoomLevelConfigResponse` (cross-package reuse). |
| `RoomLevelRewardResponse` | Single-field `List<RewardItem>` wrapper. |
| `SignItem` | One sign-in day — 9 fields, no duplicate. |
| `VoiceSignPanelResponse` | Sign-in calendar payload — 3 fields. |
| `VoiceTasksResponse` | Voice task definitions with `@Nullable` items accessor. |

## Dependencies

- **Inbound**: Angular frontend consumes the REST endpoints.
- **Outbound**: `client.JilaliClient` + `client.JilaliGateway`; ALSO imports from `room.dto` (the `RoomLevelConfigResponse` cross-package reuse — see Comments below).

## ⚠ Cross-package duplicate finding

`com.jilali.signin.dto.RewardItem` is an **exact field-for-field clone** of the nested `com.jilali.room.dto.RoomLevelConfigResponse.RewardItem`. Both 8 fields, names, types, `@JsonProperty` annotations match. The only delta: `multi_name` is `@Nullable` in the room copy, non-nullable in the signin copy.

This is the textbook shape for a "shared model in the wrong place." In the target architecture, the `RewardItem` shape belongs ONCE in either `com.jilali.platform.models` (if many features would want it) or specifically inside the room feature's domain, with signin importing/returning it rather than redeclaring.

## Improvement opportunities

1. **High**: lift `RewardItem` to a single shared location, eliminate the duplicate declaration. **Medium**: lift or unify the `RoomLevel*` response shells (or at minimum the `RewardItem` part) — three different "room + reward level" payload shapes exist across `signin/dto` and `room/dto`.
2. **Medium**: `StructuredTaskScope` in `SigninController.roomLevelBundle` is correct usage but should be tested under load (virtual thread scaling).
3. **Low**: `ClaimRewardRequest` / `ClaimTaskRewardRequest` are near-mirror shapes (2 vs 3 fields). A sealed interface for "claim requests" would consolidate.
