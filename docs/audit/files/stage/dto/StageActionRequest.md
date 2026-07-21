# StageActionRequest

`src/main/java/com/jilali/stage/dto/StageActionRequest.java`

## Purpose
Request body for the two simplest stage actions — `join` and `quit`. Carries only the room channel name and business type.

## Responsibilities
Transport `cname` + `busi_type` from client to `JilaliGateway.stageJoin`/`stageQuit`.

## Public API
Record `StageActionRequest`:
- `String cname` — `@NotBlank`, non-null.
- `int busiType` (`@JsonProperty("busi_type")`) — primitive, always present (defaults to 0 if omitted in JSON).

## Dependencies
- `@Serdeable`, `@JsonProperty`, `@NotBlank`.
- Depended on by: `StageController` (join/quit), `JilaliGateway`, `JilaliClient`.

## Coupling and cohesion analysis
Minimal and cohesive. This is the base shape (cname + busiType) that every other stage-action DTO extends by adding fields.

## Code smells
- **Data Class** (expected for a DTO).
- **Primitive Obsession:** `busiType` is an untyped `int` discriminator.
- Serves as the **root of a Duplicated-DTO family** — see package doc.

## Technical debt
`busiType` has no validation or enum; 0 is silently valid.

## Duplicate logic
Its `{cname, busiType}` pair is a strict subset of `RaiseHandRequest`, `KickRequest`, `RaiseHandApprovalRequest`, `StageInviteRequest`, `StageInviteApprovalRequest`, `DeviceControlRequest`. See overlap table in `docs/audit/packages/stage-dto.md`.

## Dead or unused code
None — used by two routes; Jackson accessors are live.

## Java 25 modernization opportunities
Ideal base of a `sealed interface StageMemberAction { String cname(); int busiType(); }` permitting all 7 action records; pattern-matching `switch` in the gateway could dispatch.

## Micronaut built-in opportunities
`@Min(0)` (micronaut-validation) on `busiType` would edge-validate the discriminator.

## Refactoring recommendations
Promote to the shared contract of a consolidated stage-action command family; add an enum for `busiType`.
