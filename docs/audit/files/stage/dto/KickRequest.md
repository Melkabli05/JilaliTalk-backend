# KickRequest

`src/main/java/com/jilali/stage/dto/KickRequest.java`

## Purpose
Request body for `POST /kick` — a moderator removing user `userId` from the stage of room `cname`.

## Responsibilities
Transport `cname`, target `user_id`, `busi_type` to `JilaliGateway.stageKick`.

## Public API
Record `KickRequest`:
- `String cname` — `@NotBlank`.
- `long userId` (`@JsonProperty("user_id")`) — `@Positive`; the *target* user.
- `int busiType` (`@JsonProperty("busi_type")`).

## Dependencies
- `@Serdeable`, `@JsonProperty`, `@NotBlank`, `@Positive`.
- Depended on by: `StageController.kick`, `JilaliGateway`, `JilaliClient`.

## Coupling and cohesion analysis
Cohesive; = `StageActionRequest` + `userId`. No trailing field ordering issue (userId before busiType).

## Code smells
- **Data Class**; **Primitive Obsession** (`busiType`).
- **Missing authorization context:** the actor is not in the payload (correctly derived from the JWT), but nothing marks this as a privileged/moderator-only action.
- Member of the duplicated stage-action DTO family.

## Technical debt
No enum for `busiType`; the moderator authority check is delegated entirely upstream.

## Duplicate logic
`{cname, userId, busiType}` is byte-for-byte the same field set as `RaiseHandApprovalRequest` minus `approvalType`, and identical shape family to `StageInviteRequest`. See package overlap table.

## Dead or unused code
None.

## Java 25 modernization opportunities
`record Kick(...) implements StageMemberAction` in a sealed command family; the "targets a user" trait could be a second sealed sub-interface `TargetedStageAction { long userId(); }`.

## Micronaut built-in opportunities
- **micronaut-security:** kick is a moderator action; a `@Secured` role/room-moderator guard at the controller would add defense-in-depth.
- `@Min` on `busiType`.

## Refactoring recommendations
Consolidate into the action command family; introduce a `TargetedStageAction` contract shared with invite/approval; add server-side moderator verification.
