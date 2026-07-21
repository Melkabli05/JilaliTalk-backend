# `com.jilali.vip` + `vip.dto` — VIP experience card (promo) feature

## Purpose

VIP-experience-card trial/gifting/promotion feature: users can claim limited VIP trials ("VIP experience cards") that grant temporary access to premium features (e.g. room-creation limits, visibility boosts). Smaller, mostly self-contained promo flow.

## File responsibilities (1 root + 11 dto = 12 files)

### Root

| File | One-line summary |
|---|---|
| `VipExperienceCardController.java` | The single controller for `/api/vip/.../*` endpoints. |

### DTOs (11) — justified hierarchical decomposition

| DTO | Layer | Purpose |
|---|---|---|
| `VipExperienceCard.java` | base | Card identification / basic metadata. |
| `VipExperienceCardDetail.java` | one level deeper | Adds rich features (only when `with_detail=true`). |
| `VipExperienceCardFeature.java` | leaf | One feature row on a card (`card_features[]`). |
| `VipExperienceCardUsedFeature.java` | post-use | One feature consumed (only after the card was actually used). |
| `VipExperienceCardRecordsRequest`, `VipExperienceCardRecordsResponse` | bulk | Paged list of cards. |
| `ClaimVipTrialResponse.java` | response | Claim outcome. |
| `ReceiveFriendSentCardRequest`, `UseVipExperienceCardRequest` | operations | Card-activation events. |
| `VipFeatureRightRequest`, `VipFeatureRightResponse` | permissions | Per-feature check. |

The `Card → Detail → Feature → UsedFeature` decomposition is **justified** (per the audit agent): each layer maps a distinct upstream JSON block with its own nullability rules (`detail` and `card_features` only populated when explicitly requested; `used_features` only after first use). Collapsing would just push nullable fields around.

## ⚠ Comments from the audit

- **`VipExperienceCardController` authorization gap**: no `@Secured`/principal binding, and `userId` comes from query/body on every endpoint (except `claimTrial` which delegates identity to `JilaliGateway`). Any authenticated caller can read or activate any user's card. NO idempotency token on `use`/`receiveFriendCard` → client retries can double-claim.
- **`card_type` type inconsistency across the family**: `int` on `VipExperienceCardDetail`, `String` on `VipExperienceCardFeature` and `VipExperienceCardUsedFeature` — real upstream contract drift surviving into the DTOs, flagged for cross-DTO cleanup.
- `VipExperienceCardFeature.ext` is correctly kept as a raw `String` (upstream JSON-in-string, Javadoc-explained).
- `VipExperienceCardRecordsResponse.cards()` has a clean null-safe default to `List.of()` for upstream's `"cards": null` empty case.

## Dependencies

- **Inbound**: Angular frontend consumes the REST endpoints.
- **Outbound**: `client.JilaliClient`, `client.ProfileClient`, `core`.

## Improvement opportunities

1. **CRITICAL — same security gap class as `manager`**: add authorization checks that the inbound user matches the `userId` on `use`/`receive` endpoints, and add idempotency tokens (`X-Idempotency-Key` or upstream's own) for `use`/`receiveFriendCard`.
2. **Medium**: reconcile `card_type` type across `Detail`/`Feature`/`UsedFeature` — pick one (`int`, with the upstream `String` ids as a separate `card_type_name` field) and standardize.
3. **Low**: when the broader `client` circular-dep refactor lands, `VipExperienceCardController` should not depend on feature-package DTOs from other packages.
