# `com.jilali.stage` — voice-room stage mechanics

## Purpose

Voice-room "stage" commands: who is currently on stage (speaking), raise-hand requests/approvals, invites to stage, moderator kick/mute/device-control. Sits adjacent to `realtime` (which delivers the resulting notifies) and `room` (which owns the room lifecycle).

## File responsibilities (1 root + 10 dto = 11 files)

### Root

| File | One-line summary |
|---|---|
| `StageController.java` | The single controller for `/api/stage/*` endpoints. Like many controllers in this codebase, it is plausibly overloaded — re-check whether the DTO count (~10) suggests too many near-mirror "command" shapes. |

### DTOs (10) — likely a sealed-interface command-pattern candidate

Most `*Request` records here are essentially thin wrappers over `(cname, userId, …)`: a candidate for refactor into a Java 25 `sealed interface StageAction` with a per-action-case hierarchy (already consistent with `im.dto.ImRealtimeEvent`'s use of `sealed interface`).

| DTO | Likely-purpose |
|---|---|
| `DeviceControlRequest` | Toggle mic/camera for a specific speaker — `{cname, userId, device: String, state: String}`. |
| `KickRequest` | Stage-kick command — `{cname, userId}`. |
| `PublisherTokenResponse` | The Agora RTC publisher-role token returned by the controller. |
| `RaiseHandRequest`, `RaiseHandApprovalRequest` | Raise-hand queue commands. |
| `StageActionRequest` | A more generic "perform stage action" envelope. |
| `StageInviteRequest`, `StageInviteApprovalRequest` | Stage-invite lifecycle. |
| `StageListResponse`, `StageMember` | List-current-stage-occupants payload. |

## Dependencies

- **Inbound**: Angular frontend consumes the REST endpoints.
- **Outbound**: `client` (JilaliClient for upstream stage calls), `core` (filter-aware types).
- Tightly coupled with `realtime` (which delivers stage-event notifies back to the browser) — but the dependency is on the event-types seam, not on this package directly.

## Improvement opportunities

1. **High — request DTO clustering**: nearly all 7 `*Request` DTOs share a common shape `(String cname, Long userId)` plus 1-2 action-specific fields. Refactor into a Java 25 sealed `StageAction` interface + per-action variants + `ApplicationEventPublisher` (`@EventListener` pattern) for the side effects.
2. **Medium — server-side authorization**: does `StageController` verify that the caller has moderator rights in `cname` before executing kick/mute/promote? The `manager` audit found this absent for manager-role controllers; check here too.
3. **Low**: `PublisherTokenResponse` is the only stage-side DTO that's a server-response, and its real-world shape may be reused across stage endpoints (verify).
