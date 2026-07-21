# StageInviteRequest

`src/main/java/com/jilali/stage/dto/StageInviteRequest.java`

## Purpose
Request body for `POST /invite` — a moderator inviting user `userId` up to the stage.

## Responsibilities
Transport `invite_type`, target `user_id`, `cname`, `busi_type` to `JilaliGateway.stageInvite`.

## Public API
Record `StageInviteRequest` (order: inviteType, userId, cname, busiType):
- `int inviteType` (`@JsonProperty("invite_type")`).
- `long userId` (`@JsonProperty("user_id")`) — `@Positive`; target.
- `String cname` — `@NotBlank`.
- `int busiType` (`@JsonProperty("busi_type")`).

## Dependencies
- `@Serdeable`, `@JsonProperty`, `@NotBlank`, `@Positive`.
- Depended on by: `StageController.invite`, `JilaliGateway`, `JilaliClient`.

## Coupling and cohesion analysis
Cohesive; = `KickRequest` fields + `inviteType`. Again a different field ordering from siblings.

## Code smells
- **Data Class**; **Primitive Obsession** (`inviteType`, `busiType`).
- **Inconsistent ordering** across family.
- Moderator action, no in-BFF authorization marker.

## Technical debt
`inviteType` undocumented; delegated authorization.

## Duplicate logic
`{cname, userId, busiType}` identical to `KickRequest`/`RaiseHandApprovalRequest`; adds `inviteType`, which also appears in `StageInviteApprovalRequest`. See package overlap table.

## Java 25 modernization opportunities
`record StageInvite(...) implements TargetedStageAction`; `inviteType` → shared enum with invite approval.

## Dead or unused code
None.

## Micronaut built-in opportunities
`@Secured` moderator guard; `@Min/@Max` on discriminators.

## Refactoring recommendations
Consolidate; share an `InviteType` enum with `StageInviteApprovalRequest`; normalize order.
