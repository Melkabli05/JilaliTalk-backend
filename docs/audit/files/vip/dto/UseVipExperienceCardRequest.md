# UseVipExperienceCardRequest

## Purpose
Request body for `POST /api/vip-experience-card/use` — activates one perk of an owned VIP experience card (the "claim" action). For the 24h VIP perk, callers pass `sceneId=30000`, `featureId=00001`.

## Public API
Record `UseVipExperienceCardRequest`:
- `@JsonProperty("card_id") @Positive long cardId` — id of the owned card. Must be > 0.
- `@JsonProperty("feature_id") @NotBlank String featureId` — perk identifier (e.g. `00001`).
- `@JsonProperty("scene_id") @NotBlank String sceneId` — feature scene (e.g. `30000` for VIP trial).
- `@JsonProperty("user_id") @Positive long userId` — acting user. Must be > 0.
- `@NotBlank String version` — card version string. Must be non-blank.

## Coupling
Validated via `jakarta.validation`; consumed by `VipExperienceCardController.use` and forwarded to `VipExperienceCardClient.useCard`.

## Notes
`userId` is taken from the body rather than from the auth principal — see the `VipExperienceCardController` note on authorization. `featureId`/`sceneId` are typed `String` rather than the typed constants used elsewhere in the codebase, consistent with upstream's stringly-typed payload.
