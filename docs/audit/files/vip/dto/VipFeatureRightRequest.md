# VipFeatureRightRequest

## Purpose
Query payload for `GET /api/vip-experience-card/feature-right` — asks upstream whether a given VIP experience-card feature (e.g. the 24h VIP perk, `scene_id=30000` / `feature_id=00001`) is currently active for a user.

## Public API
Record `VipFeatureRightRequest`:
- `@JsonProperty("user_id") @Positive long userId` — user to check. Must be > 0.
- `@JsonProperty("feature_id") @NotBlank String featureId` — perk identifier (e.g. `00001`). Must be non-blank.
- `@JsonProperty("scene_id") @NotBlank String sceneId` — perk scene (e.g. `30000` for VIP trial). Must be non-blank.

## Coupling
Built by `VipExperienceCardController.featureRight` and forwarded to `VipExperienceCardClient.queryUserFeatureRight`.

## Notes
Same authorization concern as the controller notes — `user_id` is a body/query parameter rather than being derived from the auth principal, so any caller can probe any user's feature state.
