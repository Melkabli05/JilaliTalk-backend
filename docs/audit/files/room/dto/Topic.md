# Topic.java

`src/main/java/com/jilali/room/dto/Topic.java`

## Purpose
A child topic within a `Category`.

## Responsibilities
Carry topic id, name, and its parent category id.

## Public API (record fields)
- `long id`
- `String name`
- `@JsonProperty("category_id") long categoryId`

## Dependencies
- Imports `@JsonProperty`, `@Serdeable`.
- Contained in `Category.topics`; produced by `JilaliClient.categoryTopicList`.

## Coupling and cohesion analysis
Cohesive, minimal.

## Code smells
None beyond **Data Class** (expected).

## Technical debt
None.

## Duplicate logic
`id`/`name`/`categoryId` overlaps the id/name subset of `CategoryTopicTag` (topicId/topicName/categoryId) — same conceptual entity, different projection.

## Dead or unused code
None.

## Refactoring recommendations
None material.
