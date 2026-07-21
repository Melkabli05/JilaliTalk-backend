# VipExperienceCardDetail

## Purpose
The detail sub-block of `VipExperienceCard` — card-level config (duration, version, type, status) and the list of perks the card unlocks when activated.

## Public API
Record `VipExperienceCardDetail`:
- `int id` — detail-row id (distinct from `cardId` upstream).
- `@JsonProperty("card_id") long cardId` — owning card id.
- `@JsonProperty("c_id") int cId` — upstream campaign/template id.
- `long duration` — total card duration in seconds.
- `String version` — card version.
- `@JsonProperty("card_type") int cardType` — card-type discriminator.
- `@JsonProperty("card_features") @Nullable List<VipExperienceCardFeature> cardFeatures` — perks the card can unlock; null when caller omitted `with_detail`.
- `int status` — upstream status code.
- `@JsonProperty("time_type") int timeType` — time-granularity hint upstream uses.
- `@JsonProperty("receive_use_expire_duration") long receiveUseExpireDuration` — grace window after first use (seconds).

## Coupling
Serialized via Micronaut Serde; nested inside `VipExperienceCard.detail`.

## Notes
Part of the hierarchical decomposition. The detail-vs-card split is somewhat artificial — both share `id`-like fields and `card_type` appears in three of the four records — but it cleanly maps the upstream JSON, so leaving it is faithful to the wire format.
