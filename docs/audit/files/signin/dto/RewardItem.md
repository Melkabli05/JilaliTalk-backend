# RewardItem

## Purpose
Single reward entry inside `RoomLevelRewardResponse.items` — describes one gift/card reward at a room level.

## Public API
Record `RewardItem`:
- `int id` — reward id.
- `@JsonProperty("gift_id") int giftId` — backing gift id.
- `int type` — reward type.
- `@JsonProperty("card_type") int cardType` — card-type discriminator.
- `String name` — display name.
- `int number` — quantity awarded.
- `String icon` — icon URL.
- `@JsonProperty("multi_name") String multiName` — multi-locale name suffix (non-nullable here).

## Coupling
Serialized via Micronaut Serde; appears as `List<RewardItem>` inside `RoomLevelRewardResponse`.

## Notes
Field-by-field duplicate of the nested `RewardItem` inside `com.jilali.room.dto.RoomLevelConfigResponse` — every field name, type, and `JsonProperty` matches. The only delta is `multi_name` is `@Nullable` in the `room.dto` copy. Cross-package duplication flag for the table.