# `com.jilali.vip.dto` — VIP experience-card shapes

## Files (11)

Justified hierarchical decomposition per the audit agent:

| DTO | Layer | Conditional nullability |
|---|---|---|
| `VipExperienceCard` | base | Always populated. |
| `VipExperienceCardDetail` | one level deeper | Only when `with_detail=true`. |
| `VipExperienceCardFeature` | leaf (card_features[]) | Only when the card has features. |
| `VipExperienceCardUsedFeature` | post-use (used_features[]) | Only after the card was actually used. |
| `VipExperienceCardRecordsRequest`, `VipExperienceCardRecordsResponse` | bulk list request/response (page-num + per-page params + paged cards list). | — |
| `ClaimVipTrialResponse` | response | Trial-claim outcome. |
| `ReceiveFriendSentCardRequest`, `UseVipExperienceCardRequest` | operation bodies | — |
| `VipFeatureRightRequest`, `VipFeatureRightResponse` | per-feature permission check. | — |

## ⚠ Findings (carried forward from package + controller docs)

- **Authorization gap on `VipExperienceCardController`**: no `@Secured`/principal binding, userId from query/body on every endpoint except `claimTrial`. Any authenticated caller can act on any user's card.
- **No idempotency** on `use`/`receiveFriendCard` — client retries can double-claim.
- **`card_type` type drift across the family**: `int` on `Detail` vs `String` on `Feature` and `UsedFeature` — real upstream contract drift. Standardize.
- `VipExperienceCardFeature.ext` is correctly kept as raw `String` (Javadoc explains upstream is JSON-in-string).
- `VipExperienceCardRecordsResponse.cards()` has a clean null-safe default to `List.of()`.

## Improvement opportunities

1. **CRITICAL**: add authorization + idempotency to `VipExperienceCardController` (same class of gap as `manager`).
2. **Medium**: standardize `card_type` type (pick one — `int` with separate `card_type_name: String` if upstream IDs stay `String`).
3. **Low**: keep `ext: String` documentation clear.
