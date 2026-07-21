# Category.java

`src/main/java/com/jilali/room/dto/Category.java`

## Purpose
A room-browsing category (with optional styling and nested topics) from the category/topic list.

## Responsibilities
Carry category id/name/colors and its child `Topic` list.

## Public API (record fields)
- `long id`
- `String name`
- `@JsonProperty("bg_color") @Nullable String bgColor`
- `@JsonProperty("font_color") @Nullable String fontColor`
- `@Nullable List<Topic> topics` — child topics.
- `List<Topic> topics()` — coalescing accessor returning `List.of()` when null.

## Dependencies
- Imports `Topic`, `@JsonProperty`, `@Nullable`, `@Serdeable`.
- Contained in `CategoryTopicListResponse.items`; produced by `JilaliClient.categoryTopicList`.

## Coupling and cohesion analysis
Cohesive; composes `Topic` cleanly.

## Code smells
- Same coalescing-accessor duplication as `BatchQueryResponse`/`ChannelListResponse`.
- `bgColor`/`fontColor` string pair overlaps with `CategoryTopicTag` styling fields (see Duplicate logic).

## Technical debt
Color fields are loose strings shared across category-related DTOs with no shared style type.

## Duplicate logic
`bgColor`/`fontColor` appear in `CategoryTopicTag` and `VoiceRoomInfoResponse.ChannelInfo.CategoryTopicTag` too — a recurring styling-pair.

## Dead or unused code
None.

## Refactoring recommendations
Extract a `TagStyle(bgColor, fontColor)` value type reused by category DTOs.
