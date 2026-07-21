# SignItem

## Purpose
Single entry on the voice sign-in calendar — one day in `VoiceSignPanelResponse.signList`.

## Public API
Record `SignItem`:
- `@JsonProperty("sign_day") int signDay` — day index on the calendar.
- `@JsonProperty("gift_id") int giftId` — gift id awarded on this day.
- `@JsonProperty("gift_info") String giftInfo` — supplementary gift info string.
- `@JsonProperty("gift_type") int giftType` — gift type code.
- `@JsonProperty("gift_number") int giftNumber` — quantity awarded.
- `@JsonProperty("sign_status") int signStatus` — signed / unsigned / future status.
- `@JsonProperty("to_day") boolean toDay` — whether this is today's slot.
- `@JsonProperty("gift_name") String giftName` — display name of the gift.
- `String thumb` — thumbnail URL.

## Coupling
Serialized via Micronaut Serde; appears as `List<SignItem>` inside `VoiceSignPanelResponse`.

## Notes
Unlike `RewardItem`/`RoomLevelConfigResponse.RewardItem` this record has no cross-package duplicate.