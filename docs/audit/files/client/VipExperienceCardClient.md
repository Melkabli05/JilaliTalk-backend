# VipExperienceCardClient.java

`src/main/java/com/jilali/client/VipExperienceCardClient.java`

## Purpose
Declarative `@Client` for the VIP experience-card endpoint family, which lives under a *different* upstream path (`/member_privilege_center/v3/vip_experience_card`) than the rest of the `/livehub` surface. Split out only because a `@Client(path=...)` prefix applies to every method, so these can't fold into `JilaliClient` without per-call path escaping.

## Responsibilities
- Map 4 VIP-card endpoints: `query_user_feature_right`, `query_user_record`, `user_use_card`, `receive_friend_sent_card`.
- Return `JilaliEnvelope<T>` (same envelope as `JilaliClient`).

## Public API
Interface `VipExperienceCardClient`, `@Client(id = "jlhub", path = "/member_privilege_center/v3/vip_experience_card")`:
- `JilaliEnvelope<VipFeatureRightResponse> queryUserFeatureRight(@Body VipFeatureRightRequest)`
- `JilaliEnvelope<VipExperienceCardRecordsResponse> queryUserRecord(@Body VipExperienceCardRecordsRequest)`
- `JilaliEnvelope<Object> useCard(@Body UseVipExperienceCardRequest)`
- `JilaliEnvelope<Object> receiveFriendSentCard(@Body ReceiveFriendSentCardRequest)`

## Dependencies
- Imports `JilaliEnvelope` and 6 `vip.dto` types.
- Depended on by (grep): `JilaliGateway` (for `claimVipTrial`), `VipExperienceCardController`. Called via generated proxy.

## Coupling and cohesion analysis
Small, highly cohesive (single VIP-card domain, shared path prefix and envelope). Coupling limited to `vip.dto`. Correct, well-justified split from `JilaliClient` on the real path-prefix axis (Javadoc explains it).

## Code smells
- `useCard`/`receiveFriendSentCard` return `JilaliEnvelope<Object>` — untyped success payload (minor Primitive Obsession / missing DTO), acceptable when the response body is genuinely empty on success.

## Technical debt
Negligible. The two `<Object>` returns could be `<Void>`/typed if the upstream success body were modeled.

## Duplicate logic
None. Does not overlap `JilaliClient` (disjoint path/endpoints) or `ProfileClient`. This is the correct home for VIP-card calls.

## Dead or unused code
- `queryUserFeatureRight` and `receiveFriendSentCard`: verify call sites — both flow through `VipExperienceCardController` (outside this batch). `queryUserRecord`/`useCard` are used by `JilaliGateway.claimVipTrial`. `@Client` methods are invoked via proxy, so not dead by grep of the interface. No confirmed dead code within this batch's visibility.

## Java 25 modernization opportunities
- Model `useCard`/`receiveFriendSentCard` success bodies as records if non-empty; otherwise `JilaliEnvelope<Void>`.
- Nothing imperative to modernize (declarative interface).

## Micronaut built-in opportunities
- Same as `JilaliClient`: could carry `@Retryable`/`@CircuitBreaker`; could benefit from a centralized envelope-unwrapping `@ClientFilter` so callers skip `JilaliResponses.unwrap`.

## Refactoring recommendations
1. Keep separate (correct). Optionally type the `<Object>` returns.
2. If the codebase adopts feature-scoped `@Client` interfaces sharing `jlhub`, this is already a good example of that pattern.
