# BatchQueryResponse.java

`src/main/java/com/jilali/room/dto/BatchQueryResponse.java`

## Purpose
Wrapper for the `{items:[...]}` batch-query payload.

## Responsibilities
Hold the list of `BatchChannelStatus`; null-coalesce to empty list on access.

## Public API (record fields)
- `@Nullable List<BatchChannelStatus> items` — status items.
- `List<BatchChannelStatus> items()` — overridden accessor returning `List.of()` when null.

## Dependencies
- Imports `BatchChannelStatus`, `@Nullable`, `@Serdeable`.
- Returned by `RoomController.batchQuery`; produced by `JilaliClient.batchQueryChannel`.

## Coupling and cohesion analysis
Cohesive thin wrapper.

## Code smells
- **Repeated null-coalescing accessor pattern**: identical `items == null ? List.of() : items` idiom appears in `ChannelListResponse`, `CategoryTopicListResponse`, `Category`. Cross-DTO **duplication** of a boilerplate.

## Technical debt
The null-to-empty accessor override is copy-pasted across four DTOs.

## Duplicate logic
Same wrapper shape as `ChannelListResponse` and `CategoryTopicListResponse` (a `@Nullable List<X> items` record with a coalescing accessor) — three near-identical wrappers differing only in element type.

## Dead or unused code
None.

## Refactoring recommendations
Introduce a generic `ItemsResponse<T>` or a shared null-safe accessor helper to remove the repeated pattern (see package doc).
