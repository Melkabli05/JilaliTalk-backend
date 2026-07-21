# RaiseHandApprovalRequest

`src/main/java/com/jilali/stage/dto/RaiseHandApprovalRequest.java`

## Purpose
Request body for `POST /raise-hand/approval` — a moderator approving/rejecting a listener's raise-hand.

## Responsibilities
Transport `busi_type`, target `user_id`, `approval_type`, `cname` to `JilaliGateway.raiseHandApproval`.

## Public API
Record `RaiseHandApprovalRequest` (note field order differs from siblings — busiType first):
- `int busiType` (`@JsonProperty("busi_type")`).
- `long userId` (`@JsonProperty("user_id")`) — `@Positive`; target.
- `int approvalType` (`@JsonProperty("approval_type")`) — approve/reject discriminator.
- `String cname` — `@NotBlank`.

## Dependencies
- `@Serdeable`, `@JsonProperty`, `@NotBlank`, `@Positive`.
- Depended on by: `StageController.raiseHandApproval`, `JilaliGateway`, `JilaliClient`.

## Coupling and cohesion analysis
Cohesive; = `KickRequest` fields + `approvalType`. **Inconsistent field ordering** (busiType leads here, trails elsewhere) is a minor readability/consistency debt.

## Code smells
- **Data Class**; **Primitive Obsession** (`busiType`, `approvalType`).
- **Inconsistent ordering** across the DTO family (Shotgun-Surgery risk).
- Moderator action with no in-BFF authorization marker.

## Technical debt
`approvalType` semantics undocumented; delegated authorization.

## Duplicate logic
`{cname, userId, busiType}` identical to `KickRequest`; adds `approvalType` exactly as `StageInviteApprovalRequest` does. See package overlap table.

## Dead or unused code
None.

## Java 25 modernization opportunities
`record RaiseHandApproval(...) implements TargetedStageAction`; `approvalType` → shared `ApprovalDecision` enum (also used by invite approval).

## Micronaut built-in opportunities
`@Secured` moderator guard; `@Min/@Max` on `approvalType`/`busiType`.

## Refactoring recommendations
Consolidate; extract a shared `ApprovalDecision` enum reused by invite approval; normalize field order.
