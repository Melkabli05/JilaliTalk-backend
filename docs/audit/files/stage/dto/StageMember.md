# StageMember

`src/main/java/com/jilali/stage/dto/StageMember.java`

## Purpose
One entry in the stage roster — a user currently on stage, with identity, role and mic/cam state.

## Responsibilities
Carry per-member display and state fields.

## Public API
Record `StageMember`:
- `long userId` (`@JsonProperty("user_id")`).
- `String nickname` — nullable in practice (no `@NotBlank`, response DTO).
- `String headUrl` (`@JsonProperty("head_url")`) — `@Nullable`.
- `String nationality` — `@Nullable`.
- `int role` — role discriminator (host/moderator/speaker, untyped).
- `boolean isTurnOnMic` (`@JsonProperty("is_turn_on_mic")`).
- `boolean isTurnOnCam` (`@JsonProperty("is_turn_on_cam")`).

## Dependencies
- `@Serdeable`, `@JsonProperty`, `@Nullable`.
- Depended on by: `StageListResponse` only.

## Coupling and cohesion analysis
Highly cohesive, single-purpose read model. Correct nullability annotations on optional strings.

## Code smells
- **Primitive Obsession:** `role` is a bare `int` enum-in-disguise.

## Technical debt
`role` value space undocumented; a `Role` enum would make consumers (e.g. moderator checks) type-safe.

## Duplicate logic
None.

## Dead or unused code
None; serialized field of the stage list.

## Java 25 modernization opportunities
Already a record. `role` → `Role` enum; Micronaut Serde can map enum via `@JsonValue`/ordinal. A `boolean isModerator()` derived accessor could centralize role logic.

## Micronaut built-in opportunities
Response DTO — no request validation needed. Serde handles enums natively if `role` is promoted.

## Refactoring recommendations
Introduce a `Role` enum; consider a derived `isModerator()`/`isHost()` accessor to support any future BFF-side authorization checks.
