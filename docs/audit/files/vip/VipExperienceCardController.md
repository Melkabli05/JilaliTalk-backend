# VipExperienceCardController

## Purpose
Micronaut REST controller exposing `/api/vip-experience-card` endpoints for VIP experience cards — time-boxed perks (the 24h VIP trial is `sceneId=30000`, `featureId=00001`) that a user either earns or receives from a friend, then activates one feature at a time.

## Public API
- `VipExperienceCardController(VipExperienceCardClient client, JilaliGateway gateway)` — constructor injection of the upstream VIP card client and the Jilali gateway.
- `VipFeatureRightResponse featureRight(long userId, String featureId, String sceneId)` — `GET /feature-right`; returns whether a VIP feature is currently active for the user.
- `VipExperienceCardRecordsResponse records(long userId, boolean withValidFilter=true, boolean withDetail=true)` — `GET /records`; lists the user's owned cards.
- `HttpResponse<Void> use(@Valid UseVipExperienceCardRequest)` — `POST /use`; activates one perk of an owned card (returns 204).
- `HttpResponse<Void> receiveFriendCard(@Valid ReceiveFriendSentCardRequest)` — `POST /receive-friend-card`; accepts a friend-sent card (returns 204).
- `ClaimVipTrialResponse claimTrial()` — `POST /claim-trial`; finds and activates the calling user's unused 24h-VIP-trial card perk (the "Claim" action behind the watch-limit dialog on upstream error 190041).

## Coupling
Depends on `VipExperienceCardClient` (upstream card RPCs), `JilaliGateway` (trial-claim), `JilaliResponses.unwrap`, and the `com.jilali.vip.dto.*` records. Runs on `TaskExecutors.BLOCKING`.

## Notes
No `@Secured`/principal binding on any endpoint — `userId` is taken straight from query/body, so any authenticated caller can act on any user's card or trigger their trial claim. Authorization gap: the controller trusts the client-supplied `userId` instead of resolving it from the auth principal, and `claimTrial` derives the user identity inside `JilaliGateway` rather than here. No idempotency token on `use` or `receiveFriendCard`, so client retries can double-claim perks.
