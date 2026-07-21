# JilaliGateway.java

`src/main/java/com/jilali/client/JilaliGateway.java`

## Purpose
The seam between the application and the Jilali upstream for the handful of calls that need *real work beyond envelope unwrapping*: encrypted user-info fetch (Curve25519 + AES), binary `cc2018`-decoded room-user profile, AES-decrypted Agora publisher token, and VIP-trial claim orchestration. Plain pass-through endpoints bypass this class and are called directly from controllers via `JilaliClient` + `JilaliResponses.unwrap`.

## Responsibilities
- `@Singleton` orchestrator wrapping `JilaliClient`, `VipExperienceCardClient`, a raw `@Client("jlhub") HttpClient`, `JilaliProperties`, and `AuthTokenHolder`.
- Convenience "unwrap + throw" wrappers for the stage endpoints so callers stay clean.
- Encrypted `userInfo(long)` — builds a fully hand-rolled `HttpRequest` with ~20 `x-ht-*` headers, does Curve25519 key exchange + `EncbinUtil` encrypt/decrypt, `@Cacheable("user-info")`.
- `roomUserProfile` — decode binary payload via `Cc2018Cipher`, deserialize with a private `ObjectMapper`.
- `publisherToken` — AES-decrypt via `AgoraTokenCipher`.
- `claimVipTrial` / `currentUserId` — VIP-card business logic and JWT uid resolution.

## Public API
Class `JilaliGateway` (`@Singleton`):
- Constructor `(JilaliClient, VipExperienceCardClient, @Client("jlhub") HttpClient, JilaliProperties, AuthTokenHolder)`.
- `JilaliClient client()` — exposes raw client.
- `StageListResponse stageList(int, String)`, and `void` wrappers: `stageJoin`, `stageQuit`, `raiseHand`, `stageKick`, `raiseHandApproval`, `stageInvite`, `stageInviteApproval`, `deviceControl`.
- `boolean claimVipTrial()`.
- `Long currentUserId()`.
- `@Cacheable("user-info") UserInfo userInfo(long userId)`.
- `RoomUserProfileResponse roomUserProfile(long userId, String cname, int busiType)`.
- `PublisherTokenResponse publisherToken(String cname, byte[] agoraCipherKey)`.
- Package-private `static boolean ownsUnusedTrial(VipExperienceCard)`.
- Static constants: `VIP_TRIAL_SCENE_ID="30000"`, `VIP_TRIAL_FEATURE_ID="00001"`, `VIP_TRIAL_CARD_VERSION="v1"`.

## Dependencies
- Injects/uses: `AuthTokenHolder`, `JilaliException`, `JilaliProperties`, `JwtUtil`, `Cc2018Cipher`, `Curve25519SessionGenerator`, `EncbinUtil`, `AgoraTokenCipher`, `JilaliClient`, `VipExperienceCardClient`, Micronaut `HttpClient`, `ObjectMapper`, stage/user/vip DTOs.
- Depended on by (grep): `VipExperienceCardController`, `StageController`, `UserController`, `ProfileBundleService`, `ImEventSource`, `ImEventEnricher`, `TranslateService` (import), `TextMatcher`, `RoomUserProfileResponse`, plus auth classes. Widely used.

## Coupling and cohesion analysis
This class mixes **four unrelated responsibilities**: stage action forwarding, encrypted user-info retrieval, binary profile decoding, and VIP-card business logic. Cohesion is low — the stage helpers have nothing in common with `claimVipTrial` beyond both talking upstream. Coupling is very high: it reaches into three crypto classes, two clients, a raw HttpClient, JWT util, and properties. It also holds cross-cutting knowledge of upstream header conventions (the giant `userInfo` header block).

## Code smells
- **God Class (emerging)**: 4 distinct concerns (stage / user-info / profile / VIP) in one 300-line class — flagged as the batch's prime SOLID concern. Violates SRP.
- **Long Method**: `userInfo` (lines 205–262) — ~57 lines, builds ~20 hardcoded headers inline, does key exchange, HTTP, error handling, decrypt.
- **Feature Envy / Inappropriate Intimacy**: `ownsUnusedTrial` (162–172) and `claimVipTrial` (140–160) reach deep into `VipExperienceCard.detail().cardFeatures()`/`usedFeatures()` — VIP-domain logic that belongs in the `vip` package.
- **Primitive Obsession**: hardcoded header string values (device model `SM-A908N`, `x-ht-build="135"`, locale strings) as magic literals in `userInfo`.
- **Shotgun Surgery attractor**: the raw `HttpRequest` header block duplicates concerns that `DefaultHeadersClientFilter` handles elsewhere.

## Technical debt
- The `userInfo` header set is hand-maintained and duplicates the client filter's responsibility; drift risk is high (comment at 216–221 documents a past BAD_REQUEST bug caused by uid/token mismatch).
- Two `ObjectMapper` instances created ad hoc (static `ROOM_PROFILE_MAPPER`) instead of injecting the configured bean.
- `currentUserId()` reaches into `ServerRequestContext` (thread-local request) inside a service — hidden dependency on request scope.

## Duplicate logic
- The `x-ht-*` header assembly overlaps with `DefaultHeadersClientFilter` and with `TranslateUpstreamHeaders` (the translate package's parallel bespoke-header record). Three separate places encode HelloTalk client-identity headers.
- Stage wrapper methods are near-identical one-liners (`JilaliResponses.unwrap(client.stageX(body))`) — mechanical repetition (8×).

## Dead or unused code
None found. All public methods have call sites (grep-verified above). `ownsUnusedTrial` is used by `claimVipTrial`.

## Java 25 modernization opportunities
- The stage wrapper block is a candidate for reduction, but the real win is structural.
- `claimVipTrial`'s `Optional`-based `findFirst` + `isEmpty` could be simplified; no pattern-matching win directly.
- `roomUserProfile`'s `try/catch (RuntimeException)` + `catch (IOException)` could use `catch (RuntimeException | IOException e)` multi-catch (already Java 7, but currently split).

## Micronaut built-in opportunities
- `userInfo`'s hand-rolled header block should move into a dedicated `@ClientFilter`/`@RequestFilter` keyed to the `ht/encbin` path, the way `DefaultHeadersClientFilter` already does for other headers — Micronaut request filters are the built-in mechanism, removing the manual `HttpRequest.POST(...).header(...)` chain.
- The raw `HttpClient.toBlocking().retrieve` could be a declarative `@Client` method returning `byte[]` (like `JilaliClient.userProfile`) instead of a manual blocking call.
- `@Cacheable` is already used correctly.

## Refactoring recommendations
1. **Split the class** by concern: `StageGateway` (stage wrappers), `UserInfoGateway` (encrypted user-info), `RoomProfileGateway`, and move `claimVipTrial`/`ownsUnusedTrial` into the `vip` package (a `VipTrialService`). This directly resolves the God-Class smell.
2. Move the `userInfo` header block into a Micronaut request filter.
3. Inject the configured `ObjectMapper` instead of `new ObjectMapper()`.
