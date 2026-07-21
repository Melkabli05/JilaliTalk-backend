# `com.jilali.manager.dto` — voice-room manager-role shapes

## Files (5)

| DTO | Purpose |
|---|---|
| `ApproveManagerRequest` | `{cname, host_id, user_id, operation_type}` — discriminator-tagged approve/reject operation. |
| `Manager` | `{userId, cname, role: int, grantedAt, grantedBy}` — a single moderator record. |
| `ManagerJudgeResponse` | Server's response after a "judge manager candidacy" decision. |
| `ManagerListResponse` | Paged list of moderators. |
| `SetManagerRequest` | `{cname, host_id, userId, @Positive role, action, busi_type: int}` — grant/revoke manager role. |

## Cross-package shape overlap (suspected)

| DTO | Cross-package near-duplicate |
|---|---|
| `Manager` | Likely overlaps with `room.RoomUser`-shaped roles. |
| `SetManagerRequest` ↔ `SetManagerRequest` | The `(cname, userId)` shape is the same `(cname, userId)` skeleton as 7 of 7 stage `*Request` DTOs. |

## ⚠ Security gap (carried from the package-level doc)

`ManagerController` has **no role check, no ownership check, no `@Secured`** anywhere. Any caller able to reach the controller can promote or demote moderators or approve manager operations. Mitigated only by upstream `auth.SessionAuthClientFilter` (which confirms a logged-in user but checks neither identity-nor-role).

## Improvement opportunities

1. **CRITICAL**: add authorization before any production use, OR document that this is bounded by an upstream trusted gateway.
2. **Medium**: collapse the `(cname, userId)` skeleton across stage and manager `*Request` DTOs via a sealed Java 25 `ManagerAction` interface (similar to `StageAction` recommended refactor).
