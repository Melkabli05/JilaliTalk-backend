# ClaimVipTrialResponse

## Purpose
Response body for `POST /api/vip-experience-card/claim-trial` — a minimal boolean indicating whether an unused 24h-VIP-trial card perk was found and activated.

## Public API
Record `ClaimVipTrialResponse`:
- `boolean claimed` — `true` when the user's unused trial perk was activated, `false` when they had none left.

## Coupling
Serialized via Micronaut Serde; constructed in `VipExperienceCardController.claimTrial` directly from `JilaliGateway.claimVipTrial()` (the gateway returns the `boolean`).

## Notes
No error envelope — a `false` is indistinguishable from "claim call itself failed." The controller passes through without wrapping, so a gateway exception would propagate as a 500 instead of being folded into the response.
