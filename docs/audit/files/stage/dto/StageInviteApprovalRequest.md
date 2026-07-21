# StageInviteApprovalRequest

`src/main/java/com/jilali/stage/dto/StageInviteApprovalRequest.java`

## Purpose
Request body for `POST /invite/approval` — the invited user accepting/declining a stage invite.

## Responsibilities
Transport `cname`, `busi_type`, `invite_type`, `approval_type` to `JilaliGateway.stageInviteApproval`.

## Public API
Record `StageInviteApprovalRequest`:
- `String cname` — `@NotBlank`.
- `int busiType` (`@JsonProperty("busi_type")`).
- `int inviteType` (`@JsonProperty("invite_type")`).
- `int approvalType` (`@JsonProperty("approval_type")`).

## Dependencies
- `@Serdeable`, `@JsonProperty`, `@NotBlank`.
- Depended on by: `StageController.inviteApproval`, `JilaliGateway`, `JilaliClient`.

## Coupling and cohesion analysis
Cohesive; notably has **no `user_id`** — the actor (the invitee) is the current user, correctly derived from the JWT rather than the body. Combines the discriminators of `StageInviteRequest` (`inviteType`) and `RaiseHandApprovalRequest` (`approvalType`).

## Code smells
- **Data Class**; **Primitive Obsession** (three bare-int discriminators).
- Member of the duplicated stage-action DTO family.

## Technical debt
Three undocumented int discriminators; no enums.

## Duplicate logic
`{cname, busiType}` base shared with all; `inviteType` shared with `StageInviteRequest`; `approvalType` shared with `RaiseHandApprovalRequest`. See package overlap table.

## Dead or unused code
None.

## Java 25 modernization opportunities
`record StageInviteApproval(...) implements StageMemberAction`; reuse `InviteType` and `ApprovalDecision` enums.

## Micronaut built-in opportunities
`@Min/@Max` on all three discriminators.

## Refactoring recommendations
Consolidate; share `InviteType` + `ApprovalDecision` enums with siblings.
