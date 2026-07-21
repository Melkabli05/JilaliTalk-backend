# BatchQueryRequest.java

`src/main/java/com/jilali/room/dto/BatchQueryRequest.java`

## Purpose
Inbound body for `POST /api/rooms/batch-query` — a list of channel names to query status for.

## Responsibilities
Carry and validate a non-empty list of cnames.

## Public API (record fields)
- `@NotEmpty List<String> cnames` — channel names to query; validated non-empty.

## Dependencies
- Imports `@Serdeable`, `@NotEmpty`.
- Consumed by `RoomController.batchQuery` and forwarded via `JilaliClient.batchQueryChannel`.

## Coupling and cohesion analysis
Cohesive, minimal request DTO.

## Code smells
None beyond being a **Data Class** (expected). No per-element validation on cname strings.

## Technical debt
No size cap on `cnames` — a very large list fans a large upstream batch (mild resource concern).

## Duplicate logic
None.

## Dead or unused code
None (bound by `@Body`).

## Refactoring recommendations
Consider `@Size(max=...)` to bound batch size.
