# CategoryTopicTag.java

`src/main/java/com/jilali/room/dto/CategoryTopicTag.java`

## Purpose
The category/topic tag shown on a room listing item (`ChannelListItem`).

## Responsibilities
Carry category/topic ids, names, and styling colors for a room tag.

## Public API (record fields)
- `@JsonProperty("category_id") long categoryId`
- `@JsonProperty("category_name") String categoryName`
- `@JsonProperty("topic_id") @Nullable Long topicId`
- `@JsonProperty("topic_name") @Nullable String topicName`
- `@JsonProperty("bg_color") @Nullable String bgColor`
- `@JsonProperty("font_color") @Nullable String fontColor`

## Dependencies
- Imports `@JsonProperty`, `@Nullable`, `@Serdeable`.
- Contained in `ChannelListItem`; read by `RoomsSearchService.matchesQuery`.

## Coupling and cohesion analysis
Cohesive tag DTO.

## Code smells
- **Duplicate type**: a second `CategoryTopicTag` is nested inside `VoiceRoomInfoResponse.ChannelInfo` with an *overlapping but divergent* shape (there `categoryId`/`topicId` are `int`, plus `icon`/`selectedIcon`). Two same-named types, different id widths — **Inappropriate Intimacy / inconsistency** risk.
- `bgColor`/`fontColor` pair duplicated (see `Category`).

## Technical debt
Two `CategoryTopicTag` definitions with int-vs-long id mismatch is a latent bug source.

## Duplicate logic
Field overlap with `VoiceRoomInfoResponse.ChannelInfo.CategoryTopicTag`: `categoryId`, `categoryName`, `topicId`, `topicName`, `bgColor`, `fontColor` all present in both (differing id types).

## Dead or unused code
None.

## Refactoring recommendations
Unify the two `CategoryTopicTag` records into one canonical type (pick `long` ids), extend with the icon fields the nested one adds.
