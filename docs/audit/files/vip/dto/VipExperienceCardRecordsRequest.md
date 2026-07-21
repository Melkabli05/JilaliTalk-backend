# VipExperienceCardRecordsRequest

## Purpose
Query payload for `GET /api/vip-experience-card/records` — lists a user's VIP experience cards, with toggles for filtering and detail inclusion.

## Public API
Record `VipExperienceCardRecordsRequest`:
- `@JsonProperty("user_id") @Positive long userId` — owning user id. Must be > 0.
- `@JsonProperty("with_valid_filter") boolean withValidFilter` — `true` to exclude expired/inactive cards.
- `@JsonProperty("with_detail") boolean withDetail` — `true` to include `detail` and `card_features` per card.

## Coupling
Forwarded by `VipExperienceCardController.records` to `VipExperienceCardClient.queryUserRecord`; controller supplies defaults (`true`/`true`).

## Notes
Booleans use snake_case via `@JsonProperty` to match upstream, even though `withValidFilter`/`withDetail` would also map to themselves. Controller default of `true` for `withValidFilter` means callers cannot get unfiltered lists without explicitly setting `false` — verify against upstream whether `false` is supported (some "valid_filter" implementations reject explicit-false).
