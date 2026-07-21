# RaiseHandRequest

`src/main/java/com/jilali/stage/dto/RaiseHandRequest.java`

## Purpose
Request body for `POST /raise-hand` — a listener signalling they want to speak (or lowering their hand).

## Responsibilities
Transport `cname`, `raisehand_type`, `busi_type` to `JilaliGateway.raiseHand`.

## Public API
Record `RaiseHandRequest`:
- `String cname` — `@NotBlank`.
- `int raisehandType` (`@JsonProperty("raisehand_type")`) — untyped discriminator (raise vs lower).
- `int busiType` (`@JsonProperty("busi_type")`).

## Dependencies
- `@Serdeable`, `@JsonProperty`, `@NotBlank`.
- Depended on by: `StageController.raiseHand`, `JilaliGateway`, `JilaliClient`.

## Coupling and cohesion analysis
Cohesive; = `StageActionRequest` + one `raisehandType` field.

## Code smells
- **Data Class**; **Primitive Obsession** (`raisehandType`, `busiType` bare ints, no enum).
- Member of the **duplicated stage-action DTO family**.

## Technical debt
`raisehandType` values undocumented; no validation.

## Duplicate logic
Shares `{cname, busiType}` with all 6 sibling DTOs; adds only `raisehandType`. See package overlap table.

## Dead or unused code
None.

## Java 25 modernization opportunities
Becomes a `record RaiseHand(...) implements StageMemberAction`; `raisehandType` should be an enum, resolvable in a pattern-matching `switch`.

## Micronaut built-in opportunities
`@Min/@Max` on `raisehandType` to bound the discriminator.

## Refactoring recommendations
Fold into consolidated action command; type `raisehandType` as enum.
