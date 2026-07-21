# RoomController.java

`src/main/java/com/jilali/room/RoomController.java`

## Purpose
The BFF's public HTTP API for the LiveHub voice/live "room" feature, mounted at `/api/rooms`. Covers room discovery, room info, join bundling, audience reconciliation, batch status, reference config, and room lifecycle (create/update/end).

## Responsibilities
- Expose read pass-throughs to upstream via `JilaliClient` + `JilaliResponses.unwrap`.
- Delegate the fan-out join bundle to `RoomJoinService` and search to `RoomsSearchService`.
- Decrypt the Agora RTC token before returning room info (`decryptRtcToken`).
- Apply upstream 5xx retry to fresh-room info endpoints (`withUpstreamRetryOrRethrow`).
- Serve audience-reconciliation deltas using `RoomEventSource.audienceRevision`.
- Enforce `@Valid` on inbound bodies at the boundary; map lifecycle results to correct HTTP status.

## Public API (endpoints + methods)
- `listVoiceRooms(langId, limit, offset, refresh)` — `GET /voice`.
- `listLiveRooms(...)` — `GET /live`.
- `recommendVoiceRooms(excludeCname, scene)` — `GET /voice/recommend`.
- `recommendLiveRooms(scene)` — `GET /live/recommend`.
- `recommendSingleVoiceRoom(langId)` — `GET /voice/recommend-single`.
- `searchRooms(type, query, langId, maxPages)` — `GET /{type}/search`, delegates to search service.
- `languageGroupsVoice(scene)` — `GET /language-groups/voice`, cached.
- `languageGroupsLive()` — `GET /language-groups/live`, cached.
- `categories(busiType)` — `GET /categories`, cached.
- `voiceRoomInfo(cname)` — `GET /voice/{cname}`, retry + decrypt.
- `liveRoomInfo(cname)` — `GET /live/{cname}`, retry + decrypt.
- `joinBundle(cname, busiType)` — `GET /{cname}/join-bundle`, delegates to `RoomJoinService`.
- `audienceReconcile(cname, busiType, sinceRevision)` — `GET /{cname}/audience-reconcile`.
- `channelBasicInfo(cname)` — `GET /{cname}/basic`, returns `Map<String,Object>`.
- `batchQuery(request)` — `POST /batch-query`.
- `liveVoiceConfig()` — `GET /config`, cached.
- `createVoiceChannel(request)` — `POST /voice`, returns 201.
- `updateVoiceChannel(request)` — `POST /voice/update`, returns 204.
- `endChannel(request)` — `POST /end`, returns `Map<String,Object>`.
- `userStartedChannel(busiType)` — `GET /active`, explicit 200 with nullable body.
- `userLatestChannel(busiType)` — `GET /latest-settings`.
- Constructor injects `JilaliClient`, `JilaliProperties`, `RoomJoinService`, `RoomEventSource`, `RoomsSearchService`.
- Private: `withUpstreamRetryOrRethrow(Callable<T>)`, `decryptRtcToken(VoiceRoomInfoResponse)`.

**Public endpoint count: 21** — well past the ~15-20 God Class threshold for a Micronaut controller.

## Dependencies
- Injects: `JilaliClient`, `JilaliProperties`, `RoomJoinService`, `RoomEventSource`, `RoomsSearchService`.
- DTOs: nearly the whole `room.dto` package plus `user.dto.RoomUserListRequest`.
- Depended on BY: no direct Java caller (framework-routed). Referenced in docs of `ProfileController` (mirrors its bundle pattern).

## Coupling and cohesion analysis
Cohesion is **mixed/low**: at least four distinct concern groups live in one class (see God Class below). Coupling to `JilaliClient` is heavy and direct — most endpoints are one-line unwraps, so coupling is thin per method but broad in surface. The `audienceReconcile` method contains real business logic (revision compare + conditional upstream call) inline rather than in a service — a small leak of logic into the controller.

## Code smells
- **God Class / Large Class**: 21 public endpoints spanning Discovery, Info, Join/Reconcile, Batch/Config, and Lifecycle. Lines 67-133 (discovery + reference), 135-243 (info/join/reconcile/basic), 245-291 (batch/config/lifecycle).
- **Divergent Change / Shotgun Surgery risk**: changes to discovery, browsing, and lifecycle all force edits to this one file.
- **Business logic in controller**: `audienceReconcile` (lines 206-218) belongs in a service (mirrors `RoomJoinService`); the class doc even claims discovery has "no service layer... by design", which rationalises the leak.
- **Duplicate `decryptRtcToken`** (lines 226-238) vs identical method in `RoomJoinService` (see Duplicate logic).
- **Primitive Obsession**: `Map<String,Object>` returned raw from `channelBasicInfo`, `endChannel`, `liveVoiceConfig`, `userStartedChannel`, `userLatestChannel` — untyped pass-throughs.
- **Inconsistent typing of `categoryId`**: this file's flow uses `room.dto.CategoryTopicTag` (long ids) while the nested one in `VoiceRoomInfoResponse` uses int — latent inconsistency.

## Technical debt
- `decryptRtcToken` copy in controller throws `JilaliException` (BAD_GATEWAY) on null rtcInfo, while the service copy logs and returns — two divergent behaviours for the same operation, a subtle bug magnet.
- Untyped `Map<String,Object>` responses defer modeling debt to the frontend.
- `withUpstreamRetryOrRethrow` exists only to adapt a package-private service method's checked exception — awkward seam.

## Duplicate logic
- `decryptRtcToken` (lines 226-238) is a near-duplicate of `RoomJoinService.decryptRtcToken` (lines 182-193). Only divergence: null-rtcInfo handling (throw vs log). Prime consolidation candidate — move into `AgoraTokenCipher` or a shared helper.
- The retry mechanism itself is shared correctly (delegates to `RoomJoinService.withUpstreamRetry`), but the adapter wrapper duplicates the catch pattern documented in `RoomJoinService.joinBundle`.

## Dead or unused code
None. Every method is an `@Get`/`@Post` endpoint invoked by Micronaut routing; private helpers are all called. Not dead — framework-invoked.

## Refactoring recommendations
- Split into focused controllers: `RoomDiscoveryController` (list/recommend/search/language-groups/categories/config), `RoomInfoController` (voice/live info, basic, join-bundle, audience-reconcile), `RoomLifecycleController` (create/update/end/active/latest-settings). This directly dissolves the God Class.
- Move `audienceReconcile` logic into `RoomEventSource`-backed service or `RoomsSearchService` sibling.
- Extract the single canonical `decryptRtcToken` shared by controller + `RoomJoinService`.
- Replace `Map<String,Object>` returns with typed records where the shapes are known.
