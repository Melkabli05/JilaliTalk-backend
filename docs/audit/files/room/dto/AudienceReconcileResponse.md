# AudienceReconcileResponse.java

`src/main/java/com/jilali/room/dto/AudienceReconcileResponse.java`

## Purpose
Response for the 30s audience-drift-correction poll. Collapses the old two-round-trip (revision check, then conditional roster refetch) into one delta response.

## Responsibilities
Carry the current server-side audience revision, whether it changed, and (only when changed) the new roster.

## Public API (record fields)
- `int revision` — current server-side audience revision.
- `boolean changed` — true when revision moved past caller's `sinceRevision`.
- `@Nullable List<RoomUser> list` — roster, present only when `changed`.

## Dependencies
- Imports `RoomUser`, `@Nullable`, `@Serdeable`.
- Constructed only in `RoomController.audienceReconcile`. No external Java caller.

## Coupling and cohesion analysis
Cohesive single-purpose response DTO. Couples to `RoomUser` (shared roster element).

## Code smells
- **Data Class** — expected/acceptable for a serialized response record.

## Technical debt
None material.

## Duplicate logic
Reuses `RoomUser` rather than duplicating roster shape — good composition. No overlap.

## Dead or unused code
None. Record accessors invoked by Jackson/Serde on serialization.

## Refactoring recommendations
None needed. Well-formed.
