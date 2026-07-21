# `com.jilali.stage.dto` — voice-room stage-command shapes

## Files (10)

| DTO | Purpose |
|---|---|
| `DeviceControlRequest` | `{cname, userId, device, state}` — toggle mic/camera for a speaker. |
| `KickRequest` | `{cname, userId}` — kick from stage. |
| `PublisherTokenResponse` | Server response: Agora RTC publisher-role token. |
| `RaiseHandRequest` | `{cname, userId}` — raise hand. |
| `RaiseHandApprovalRequest` | `{cname, userId, action}` — approve/reject raised hand. |
| `StageActionRequest` | Generic "do stage action" envelope. |
| `StageInviteRequest`, `StageInviteApprovalRequest` | Stage-invite lifecycle. |
| `StageListResponse`, `StageMember` | List-current-stage-occupants payload. |

## ⚠ Same authorization gap as `manager` and `vip`

`StageController` is not itself in the dto package — but the `*Request` DTOs all share the `(cname, userId)` skeleton that gets passed verbatim. Check whether `StageController` (audited in the controller's own doc) does ANY ownership check before executing kick/mute/invite. **Likely not** — same class of bug.

## Cross-shape overlap

- 7 of 8 `*Request` DTOs share `(String cname, Long userId)` plus 0-3 action-specific fields. **Strong refactor candidate** — Java 25 `sealed interface StageAction { record RaiseHand(...), record Kick(...), ... }` with `@JsonTypeInfo` polymorphic dispatch.

## Improvement opportunities

1. **High**: collapse the 7 near-mirror `*Request` DTOs into a single `sealed interface` (the modern-Java replacement for a multi-action controller's Rube-Goldberg DTO set).
2. **High**: pair with a `@Secured`/principal ownership check in `StageController` (authorization gap to fix regardless).
3. **Low**: `PublisherTokenResponse` likely represents a single shared record shape reused across stage endpoints — verify and possibly standardize.
