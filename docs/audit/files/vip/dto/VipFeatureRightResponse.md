# VipFeatureRightResponse

## Purpose
Response body for `GET /api/vip-experience-card/feature-right` — whether a given VIP experience-card feature (e.g. the 24h VIP perk, `scene_id=30000` / `feature_id=00001`) is currently active for the user, and when it expires.

## Public API
Record `VipFeatureRightResponse`:
- `@JsonProperty("effect_status") int effectStatus` — active state code from upstream.
- `@JsonProperty("left_times") int leftTimes` — remaining activation count for quota-style features; `0` when N/A.
- `@JsonProperty("expire_at") long expireAt` — epoch millis the activation expires at.
- `@JsonProperty("time_now") long timeNow` — upstream's clock at the time of the call; client can use to reconcile skew.

## Coupling
Serialized via Micronaut Serde; returned directly by `VipExperienceCardController.featureRight`.

## Notes
Returns `expire_at` and `time_now` both as `long` (epoch millis) — consistent with the rest of the codebase's epoch-millis convention. `effect_status` and `left_times` are loosely typed ints to absorb upstream's evolving enum; downstream code should treat unknown values as "unknown" rather than failing.
