# BFF Consolidation, Round 2 — Rooms Discovery + Search + Cache Hygiene

**Date:** 2026-07-03
**Scope:** `jilalibff` (`RoomController`, `RoomJoinService`-sibling service) + `JilaliTalk-angular-frontend` (`features/rooms/**`, `core/services/create-room.service.ts`, `core/layout/header.component.ts`, `features/room/pages/room-page.ts`, `core/services/user-info.service.ts`).

## 0. Context

Round 1 (commits `dad00d0`, `e220387`, `c4a6b68`, and the frontend's matching `a1a57fc`/`ddfb9ab`/`7ea5896`) already fixed the room-*entry* waterfall (`join-bundle`), the WebSocket N+1 user-info lookups (`enrich-batch`), the audience-roster polling (`audience-reconcile`), and moved a handful of derived-field computations (`pointsTotal`, flattened `tags`, ms timestamps) server-side. That work is fully adopted by the frontend and out of scope here — this round covers what a fresh audit found *outside* that pass: the rooms-discovery/list page, a duplicated reference-data fetch, one leftover two-step path in the room feature, and a cache without eviction.

Findings not included in this round (`PermissionsService` duplication, DM/notification persistence, coin/gift stubs) are listed in §6 with the reasoning for deferring each.

## 1. Problem

Four independent, verified issues (a fifth, rooms-list/recommended bundling, was investigated and explicitly deferred — see §3.1):

1. **Categories double-fetched, double-cached client-side.** `create-room.service.ts` (via `header.component.ts`) and `RoomsStore` (via `rooms-api.ts`) each independently call `GET /rooms/categories` — two call sites, no shared Angular-side cache, even though the BFF already serves it from a 6h `@Cacheable`.
2. **`makeVisible()` still sequential.** `room-page.ts:347-381` calls `fetchJoinBundle()` then `joinRoom()` one after another for the ghost-listener-becomes-visible path — the exact shape `join-bundle` fixed for initial entry, not extended here.
3. **Search silently incomplete + sequential waterfall.** `filterRooms()` only ever filters the pages already loaded into `RoomsStore`. `computeIsAutoSearching()` compensates by auto-paginating one page at a time (up to `MAX_SEARCH_OFFSET = 200` rooms, i.e. up to 10 sequential `GET` calls) whenever a debounced search has zero local matches. Upstream (`GET /channel_list/voice`) has no `keyword` parameter (confirmed — not present anywhere in `JilaliClient.java` or the captured traffic in `websocket_realtime.md`), so search can't move upstream; it can only be shifted from "10 sequential frontend-triggered round trips" to "1 BFF round trip that fans the same pages out in parallel."
4. **`UserInfoService` is fetched-once-per-session in practice, despite its own doc comment.** `user-info.service.ts:239,294-310` — the service's cache doesn't itself gate the HTTP call (its doc comment says every `fetchUserInfo()` call reaches the BFF, which is server-side cached for 24h). The actual bug is at every call site (verified via grep — `user-info-modal.component.ts:721`, `ghost-audience.util.ts:50`, `user-action-modal.ts:798`, and others): all of them guard the call with `if (!getUserInfo(uid)) fetchUserInfo(uid)`, so once a uid is cached, `fetchUserInfo` is never invoked for it again for the rest of the session — VIP status/avatar/nickname changes never surface after first sight.

## 2. Approach

Extend the exact pattern already proven in Round 1 — no new architecture:

- **Reuse the existing `@Cacheable("reference-data")` categories endpoint** for #1 — the fix here is entirely frontend-side (one shared resource instead of two independent fetches); no backend change needed.
- **Parallelize, don't rebuild** for #2 — `makeVisible()` already has `fetchJoinBundle()` available; it just needs to stop `await`-chaining it before `joinRoom()`.
- **Bundle endpoint** (`StructuredTaskScope.open()`, mirroring `RoomJoinService.joinBundle`) for #3 (search).
- **Bounded TTL cache** for #4, matching the pattern already used for `UserInfo` server-side (`JilaliGateway`'s Caffeine cache) — same idea, client-side, much smaller scope.

## 3. Design

### 3.1 Rooms-list + recommended bundling — deferred, not in this round

Re-examined during implementation planning: unlike room-entry's four calls (which were genuinely sequential-or-blocking before `join-bundle`), `RoomsStore`'s `roomsPage` and `recommendedResource` are two independent `rxResource`s that Angular already fires concurrently — the win from bundling them is request *count*, not latency. Bundling them correctly is non-trivial: `recommendedResource` is keyed only by `type` (fetched once per type/language change), while the main list is keyed by `{type, offset, langId}` and re-fires on every `loadMore()`. A naive bundle keyed by the same params as the paginated list would make `loadMore()` needlessly re-fetch recommended too, which is a regression, not a fix — recommended is meant to be fetched once per type/language change, not once per page.

**Decision: defer this.** The correct fix (bundle only fires for the offset=0 case; pagination continues hitting the existing plain list endpoint; recommended value is held stable across loadMore) is implementable but adds real complexity for a request-count win only, with no measured evidence yet that upstream call volume from this path is a problem. Revisit if upstream rate limits or the BFF's own load become a measured concern. §3.2 (categories) and §3.4 (search) remain in scope — those are both correctness/latency issues, not just request-count.

### 3.2 Shared `CategoriesService` (new, `shared/data/`)

Verified: `rooms-model.ts` and `shared/data/categories.ts` independently declare structurally identical `Category`/`CategoryTopic` interfaces, and two separate call sites hit `GET /rooms/categories` — `header.component.ts`'s own `rxResource` (via `CreateRoomService.fetchCategories()`) and `RoomsStore`'s own `rxResource` (via `RoomsApi.fetchCategories()`). `header.component.ts` is the persistent app-shell header (mounted for the whole session), so its fetch typically fires before or alongside any feature page's.

Fix: one new root-provided `CategoriesService` in `shared/data/` (the correct layer per this codebase's dependency rules — `core → shared` and `features → shared` are both legal edges, `core → features` and cross-feature imports are not) wrapping `HttpClient` with `shareReplay({ bufsize: 1, refCount: false })` keyed by `busiType`, so concurrent or sequential callers coalesce onto one in-flight/cached HTTP call regardless of call order. `rooms-model.ts`'s duplicate `Category`/`CategoryTopic` interfaces are deleted in favor of importing from `shared/data/categories.ts`. `CreateRoomService.fetchCategories()` and `RoomsApi.fetchCategories()` both delegate to the new service instead of calling `HttpClient` directly.

### 3.3 `makeVisible()` — parallelize, don't chain

Verified in `room-page.ts:347-381`: `fetchJoinBundle()` (four read-only upstream GETs) and `joinRoom()` (`POST /users/rooms/{cname}/join`, registers presence) have no data dependency on each other — `bundle`'s fields (`voiceInfo`, `stage`, `audience`) are only read in the success path after *both* calls resolve, and neither call's input depends on the other's output. Today they run sequentially for no reason. Fix is purely frontend: fire both with `Promise.all`/`forkJoin` instead of two `await`s in series, cutting this path's wall-clock latency roughly in half. Keep distinguishable error messages by inspecting which promise rejected (`Promise.allSettled` if the two existing toasts — "room info unavailable" vs. "failed to rejoin visibly" — must stay distinct). No backend change needed.

### 3.4 Search: `GET /api/rooms/{type}/search`

```java
@Get("/{type}/search")
public ChannelListResponse searchRooms(
        String type,
        @QueryValue String query,
        @QueryValue(defaultValue = "0") int langId,
        @QueryValue(defaultValue = "5") int maxPages) { // bounded: 5 pages × 20 = 100 rooms max fan-out
    return roomsSearchService.search(type, query, langId, maxPages);
}
```

`RoomsSearchService` forks up to `maxPages` concurrent `listVoiceRooms`/`listLiveRooms` calls (offsets `0, 20, 40, ...`), concatenates, and applies the same category/language/text filter server-side (port `filterRooms()`'s text-match logic to Java — it's a simple substring match on room name/notice, see `rooms-model.ts:118-150`). Returns one `ChannelListResponse` already filtered.

Frontend: when `searchQuery.debounced()` is non-empty, `RoomsStore` swaps its `rxResource` `stream` to call `searchRooms()` once instead of driving `computeIsAutoSearching()`'s sequential-page loop. `pagination-search.util.ts`'s `computeIsAutoSearching`/auto-paginate effect can be deleted once this lands — it existed only to work around the lack of a bundled search call.

This is explicitly **not** a full-corpus search — it's bounded to `maxPages × 20` rooms, same ceiling as today's `MAX_SEARCH_OFFSET`, just fetched in one parallel round trip instead of up to 10 sequential ones. A true full-corpus search would require upstream support that doesn't exist; not pursuing that here.

### 3.5 `UserInfoService` TTL

Store `fetchedAt: number` alongside each cached `UserInfo` entry (wrap in an internal `{ info: UserInfo; fetchedAt: number }`, `getUserInfo()`'s return type is unchanged). Add `isStale(userId): boolean` (entries older than 5 minutes — matching the room heartbeat cadence's order of magnitude — are stale). Update the four call-site guards (`user-info-modal.component.ts:721`, `ghost-audience.util.ts:50`, `user-action-modal.ts:798`, and the fourth found during implementation via the same grep) from `if (!getUserInfo(uid))` to `if (!getUserInfo(uid) || isStale(uid))`. No backend change.

## 4. Files touched

**Backend (`jilalibff`):**
- `src/main/java/com/jilali/room/RoomController.java` — add `/{type}/search`
- `src/main/java/com/jilali/room/TextMatcher.java` — new, pure-logic port of `text-search.util.ts`
- `src/main/java/com/jilali/room/RoomsSearchService.java` — new

**Frontend (`JilaliTalk-angular-frontend`):**
- `shared/data/categories.service.ts` — new, shared/cached categories fetch
- `shared/data/categories.ts` — unchanged (already holds the canonical `Category`/`CategoryTopic` types)
- `features/rooms/data/rooms-model.ts` — delete duplicate `Category`/`CategoryTopic`, re-export from `shared/data/categories`
- `features/rooms/data/rooms-api.ts` — `fetchCategories()` delegates to `CategoriesService`; add `searchRooms()`
- `core/services/create-room.service.ts` — `fetchCategories()` delegates to `CategoriesService`
- `features/rooms/state/rooms-store.ts`, `live-rooms-store.ts` — swap to `searchRooms()` when a debounced query is present, instead of the sequential auto-paginate loop
- `features/rooms/data/pagination-search.util.ts` — remove `computeIsAutoSearching` once both stores stop using it
- `features/room/pages/room-page.ts` — `makeVisible()` parallelizes per §3.3
- `core/services/user-info.service.ts` — add TTL to cache entries; update 4 call sites' staleness guard

## 5. Testing / verification

- Backend: `TextMatcherTest` (plain JUnit5, pure logic, mirrors `JilaliGatewayTest`'s style). `RoomsSearchService`'s own upstream fan-out has no automated test, matching existing precedent — `RoomJoinService` (same `StructuredTaskScope` pattern) has none either, since this project has no mocking framework (no Mockito dependency) to fake `JilaliClient`, an `@Client` HTTP interface.
- Frontend: `CategoriesService` gets a `HttpTestingController`-based spec (first use of that tool in this repo, but it's the standard Angular-idiomatic way to test an HTTP-calling service — not an invented pattern) asserting a second `fetchCategories()` call while the first is in flight does not trigger a second HTTP request.
- `npx tsc --noEmit` clean on the frontend.
- Manual (`/run` or `/verify`): open the rooms list then open the create-room modal, confirm the Network tab shows exactly one `/rooms/categories` request for the whole session; type a search query for a room on a page not yet loaded, confirm it resolves via 1 request instead of a burst; toggle a minimized room back to visible, confirm the two requests fire concurrently in the Network tab, not sequentially.

## 6. Out of scope / deferred (needs a separate decision)

- **`PermissionsService` duplicating the role matrix client-side** — not a bug. Jilali/HelloTalk upstream is the actual authority (returns `400`/`100002` on invalid role actions per `livehub_business_rules.md`); the BFF deliberately doesn't own business logic (see `jilalibff/README.md` §"Deliberate decisions"). Client-side duplication here is legitimate UI responsiveness, not drift risk, since it's not a security boundary.
- **DM/notification history has no backend persistence** — `MessagesStore`/`NotificationStore` are localStorage + WebSocket-replay only; no REST history endpoint exists, and none can be proxied from upstream (HelloTalk's IM history isn't in the captured API surface). Fixing this means the BFF taking on its first real datastore, contradicting the "no database" principle in its README. This is a legitimate architecture decision, not a mechanical cleanup — needs its own spec and an explicit call on whether the BFF should grow a database.
- **`userCoins` hardcoded, `GiftsStore` unwired** — incomplete features, not redundant logic. No upstream wallet/coin endpoint has been captured/documented yet; needs traffic-capture research before any BFF work is possible here.
- **`features/profile/`** — unimplemented stub, nothing to consolidate yet.
