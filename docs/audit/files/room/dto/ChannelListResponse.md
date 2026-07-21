# ChannelListResponse.java

`src/main/java/com/jilali/room/dto/ChannelListResponse.java`

## Purpose
Wrapper for the `{items:[...]}` room-listing payloads.

## Responsibilities
Hold the list of `ChannelListItem`; null-coalesce to empty on access.

## Public API (record fields)
- `@Nullable List<ChannelListItem> items`
- `List<ChannelListItem> items()` — coalescing accessor.

## Dependencies
- Imports `ChannelListItem`, `@Nullable`, `@Serdeable`.
- Returned by many `RoomController` discovery endpoints and built by `RoomsSearchService.search`. Referenced in `JilaliClient`.

## Coupling and cohesion analysis
Cohesive thin wrapper — the most heavily used of the three `items` wrappers.

## Code smells
- **Duplicate wrapper shape** with `BatchQueryResponse` and `CategoryTopicListResponse`.

## Technical debt
Repeated coalescing-accessor boilerplate.

## Duplicate logic
One of three near-identical `@Nullable List<X> items` + coalescing-accessor wrappers in this package.

## Dead or unused code
None.

## Refactoring recommendations
Consolidate the three wrappers into a generic `ItemsResponse<T>`.
