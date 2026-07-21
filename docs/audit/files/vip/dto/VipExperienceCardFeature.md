# VipExperienceCardFeature

## Purpose
One perk a card unlocks, identified by `scene_id`/`feature_id` (e.g. the 24h VIP perk is `scene_id=30000`, `feature_id=00001`). Member of `VipExperienceCardDetail.cardFeatures`.

## Public API
Record `VipExperienceCardFeature`:
- `@JsonProperty("scene_id") String sceneId` — perk scene.
- `@JsonProperty("feature_id") String featureId` — perk identifier.
- `String ext` — raw JSON-encoded config blob (e.g. `{"times":0,"duration":86400,"receive_use_expire_duration":2592000}`); kept as a string rather than a typed nested object because the shape varies per feature.
- `@JsonProperty("card_type") String cardType` — card-type discriminator (note: here a `String`, but `int` on the parent `VipExperienceCardDetail`).

## Coupling
Serialized via Micronaut Serde; nested inside `VipExperienceCardDetail.cardFeatures`.

## Notes
The intentional `ext`-as-raw-string is correctly documented in the Javadoc — upstream encodes the per-feature payload as a JSON string and the shape is not stable across features, so typing it would be guessing. Real type mismatch to flag: `card_type` is `String` here but `int` on `VipExperienceCardDetail` and `VipExperienceCardUsedFeature` has it as a `String` too — inconsistent upstream contract surviving into the DTO.
