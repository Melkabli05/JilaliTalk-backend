# CategoryTopicListResponse.java

`src/main/java/com/jilali/room/dto/CategoryTopicListResponse.java`

## Purpose
Wrapper for the `{items:[...]}` category/topic list payload.

## Responsibilities
Hold the list of `Category`; null-coalesce to empty on access.

## Public API (record fields)
- `@Nullable List<Category> items`
- `List<Category> items()` — coalescing accessor.

## Dependencies
- Imports `Category`, `@Nullable`, `@Serdeable`.
- Returned by `RoomController.categories`; produced by `JilaliClient.categoryTopicList`.

## Coupling and cohesion analysis
Cohesive thin wrapper.

## Code smells
- **Duplicate wrapper shape** with `BatchQueryResponse` and `ChannelListResponse` (nullable-list + coalescing accessor).

## Technical debt
Repeated wrapper boilerplate.

## Duplicate logic
See package doc — one of three near-identical `items` wrappers.

## Dead or unused code
None.

## Refactoring recommendations
Consolidate the three `items` wrappers into a generic base.
