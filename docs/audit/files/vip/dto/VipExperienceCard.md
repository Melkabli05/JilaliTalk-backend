# VipExperienceCard

## Purpose
A VIP experience card owned by a user, as returned inside `VipExperienceCardRecordsResponse.cards`. `usedFeatures` is only populated once at least one of the card's `card_features` has been activated via `user_use_card`.

## Public API
Record `VipExperienceCard`:
- `long id` — card id (DB PK).
- `@JsonProperty("get_at") long getAt` — epoch millis the card was granted.
- `VipExperienceCardDetail detail` — nested detail block; nullable when caller passed `with_detail=false`.
- `@JsonProperty("used_features") @Nullable List<VipExperienceCardUsedFeature> usedFeatures` — activated perks, present only after first use.
- `String source` — origin/source channel of the card.
- `@JsonProperty("record_id") String recordId` — upstream record id (used to receive/delete).

## Coupling
Serialized via Micronaut Serde; nested inside `VipExperienceCardRecordsResponse.Content.cards`.

## Notes
Part of the `VipExperienceCard → Detail → Feature → UsedFeature` hierarchy. Justified composition here — each layer has a distinct lifecycle and the upstream wire-format genuinely splits them, so collapsing would just push nullable fields around. `used_features` could plausibly fold into `detail` since the detail is also nullable-on-demand and both belong to the same card, but upstream returns them as siblings, so keeping them flat matches reality.
