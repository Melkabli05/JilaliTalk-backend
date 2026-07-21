# VipExperienceCardUsedFeature

## Purpose
An activated perk on a `VipExperienceCard` — populated inside `VipExperienceCard.usedFeatures` after the user has run `use` at least once. Records the activation timestamp, upstream `relate_id`, write-off flag, and the social/source context that triggered it.

## Public API
Record `VipExperienceCardUsedFeature`:
- `@JsonProperty("scene_id") String sceneId` — perk scene.
- `@JsonProperty("feature_id") String featureId` — perk identifier.
- `@JsonProperty("used_at") long usedAt` — epoch millis the perk was activated.
- `@JsonProperty("card_type") String cardType` — card-type discriminator.
- `@JsonProperty("relate_id") String relateId` — upstream relation id (often an event/campaign id).
- `@JsonProperty("is_write_off") int isWriteOff` — write-off flag (0/1).
- `@JsonProperty("used_user_version") String usedUserVersion` — user-version snapshot at use time.
- `String source` — origin channel of the activation.
- `@JsonProperty("push_campaign_id") String pushCampaignId` — campaign that drove the use, if any.
- `@JsonProperty("message_id") String messageId` — message that delivered the card, if any.

## Coupling
Serialized via Micronaut Serde; nested inside `VipExperienceCard.usedFeatures`.

## Notes
Part of the hierarchical decomposition. The fields fall into three groups (perk id, use event, social provenance); a single flat record is fine since they're all populated/unpopulated together per use event. `is_write_off` as `int` rather than `boolean` is consistent with upstream returning 0/1; flag for any cross-DTO consistency cleanup.
