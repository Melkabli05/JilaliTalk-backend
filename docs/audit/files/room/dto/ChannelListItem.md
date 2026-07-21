# ChannelListItem.java

`src/main/java/com/jilali/room/dto/ChannelListItem.java`

## Purpose
A single room-listing item shared across discovery endpoints (channel_list, recommend, end-page). Intentionally limited to fields a frontend renders.

## Responsibilities
Compose the summary room (`Channel`), its host, current members, join token, background, and category/topic tag.

## Public API (record fields)
- `Channel channel` — summary room block.
- `@JsonProperty("host_user") HostUser hostUser` — host.
- `@Nullable List<RoomUser> users` — sampled members.
- `@Nullable String token` — join token.
- `@JsonProperty("background_url") @Nullable String backgroundUrl`
- `@JsonProperty("category_topic_tag") @Nullable CategoryTopicTag categoryTopicTag`

## Dependencies
- Imports `Channel`, `HostUser`, `RoomUser`, `CategoryTopicTag`.
- Contained in `ChannelListResponse`; consumed by `RoomsSearchService`; returned by `RoomController.recommendSingleVoiceRoom`. Referenced in `JilaliClient`.

## Coupling and cohesion analysis
Good composition — aggregates smaller DTOs rather than inlining fields. This is the *right* pattern the user-shaped DTOs elsewhere fail to follow.

## Code smells
- Minor: `matchesQuery` in the search service reaches through this graph (Feature Envy lives on the caller, not here).

## Technical debt
None material.

## Duplicate logic
Composes `HostUser` + `RoomUser` — but note those two duplicate identity fields between themselves (see their docs / package doc), so this item indirectly carries that redundancy.

## Dead or unused code
None.

## Refactoring recommendations
Consider adding a `searchableText()` method here so `RoomsSearchService` stops reaching into internals.
