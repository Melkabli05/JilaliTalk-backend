# BFF Consolidation, Round 2 — Rooms Discovery + Search + Cache Hygiene

**Date:** 2026-07-03
**Scope:** `jilalibff` (`RoomController`, `RoomJoinService`-sibling service) + `JilaliTalk-angular-frontend` (`features/rooms/**`, `core/services/create-room.service.ts`, `core/layout/header.component.ts`, `features/room/pages/room-page.ts`, `core/services/user-info.service.ts`).

## 0. Context

Round 1 (commits `dad00d0`, `e220387`, `c4a6b68`, and the frontend's matching `a1a57fc`/`ddfb9ab`/`7ea5896`) already fixed the room-*entry* waterfall (`join-bundle`), the WebSocket N+1 user-info lookups (`enrich-batch`), the audience-roster polling (`audience-reconcile`), and moved a handful of derived-field computations (`pointsTotal`, flattened `tags`, ms timestamps) server-side. That work is fully adopted by the frontend and out of scope here — this round covers what a fresh audit found *outside* that pass: the rooms-discovery/list page, a duplicated reference-data fetch, one leftover two-step path in the room feature, and a cache without eviction.

Findings not included in this round (`PermissionsService` duplication, DM/notification persistence, coin/gift stubs) are listed in §6 with the reasoning for deferring each.

## 1. Problem

Four independent, verified issues:

1. **Rooms-list page fires 3 unbundled calls.** `RoomsStore`/`LiveRoomsStore` (`features/rooms/state/*.ts`) each run three separate `rxResource`s on load — main list (`GET /rooms/{type}`), recommended list (`GET /rooms/{type}/recommend`), categories (`GET /rooms/categories`). No aggregation, unlike room-entry's `join-bundle`.
2. **Categories double-fetched, double-cached client-side.** `create-room.service.ts` and `header.component.ts` both independently call `GET /rooms/categories` (for the create-room modal) while `RoomsStore` calls it again for the browse page — three call sites, no shared Angular-side cache, even though the BFF already serves it from a 6h `@Cacheable`.
3. **`makeVisible()` still sequential.** `room-page.ts:347-381` calls `fetchJoinBundle()` then `joinRoom()` one after another for the ghost-listener-becomes-visible path — the exact shape `join-bundle` fixed for initial entry, not extended here.
4. **Search silently incomplete + sequential waterfall.** `filterRooms()` only ever filters the pages already loaded into `RoomsStore`. `computeIsAutoSearching()` compensates by auto-paginating one page at a time (up to `MAX_SEARCH_OFFSET = 200` rooms, i.e. up to 10 sequential `GET` calls) whenever a debounced search has zero local matches. Upstream (`GET /channel_list/voice`) has no `keyword` parameter (confirmed — not present anywhere in `JilaliClient.java` or the captured traffic in `websocket_realtime.md`), so search can't move upstream; it can only be shifted from "10 sequential frontend-triggered round trips" to "1 BFF round trip that fans the same pages out in parallel."
5. **`UserInfoService` cache never evicts.** `user-info.service.ts:239,294-310` — a plain `signal<Map>` with no TTL. In a long room session with audience churn, entries accumulate forever and VIP/avatar/nickname changes never surface after first fetch.

## 2. Approach

Extend the exact pattern already proven in Round 1 — no new architecture:

- **Bundle endpoints** (`StructuredTaskScope.open()`, mirroring `RoomJoinService.joinBundle`) for #1 and #4.
- **Reuse the existing `@Cacheable("reference-data")` categories endpoint** for #2 — the fix here is entirely frontend-side (one shared resource instead of three independent fetches); no backend change needed.
- **Reorder, don't rebuild** for #3 — `makeVisible()` already has `fetchJoinBundle()` available; it just needs to stop calling `joinRoom()` as a second step when the bundle can carry what's needed.
- **Bounded TTL cache** for #5, matching the pattern already used for `UserInfo` server-side (`JilaliGateway`'s Caffeine cache) — same idea, client-side, much smaller scope.

## 3. Design

### 3.1 `GET /api/rooms/{type}/page-bundle` (new)

Fans out the three rooms-list calls concurrently, same shape as `joinBundle`:

```java
// RoomController.java
@Get("/{type}/page-bundle")
public RoomsPageBundleResponse roomsPageBundle(
        String type, // "voice" | "live"
        @QueryValue(defaultValue = "0") int langId,
        @QueryValue(defaultValue = "20") int limit,
        @QueryValue(defaultValue = "0") int offset,
        @QueryValue(defaultValue = "1") int refresh) {
    return roomsPageService.pageBundle(type, langId, limit, offset, refresh);
}
```

New `RoomsPageService` (sibling to `RoomJoinService`, same `StructuredTaskScope` pattern) forks three tasks — `listVoiceRooms`/`listLiveRooms` dispatched by `type`, `recommendVoiceRooms`/`recommendLiveRooms`, and `categoryTopicList` (already `@Cacheable`, so this fork is nearly free once warm) — and returns:

```java
public record RoomsPageBundleResponse(
    ChannelListResponse rooms,
    ChannelListResponse recommended,
    CategoryTopicListResponse categories
) {}
```

Frontend: `RoomsStore`/`LiveRoomsStore` collapse their three `rxResource`s into one `rxResource<RoomsPageBundleResponse, Params>`, splitting `rooms()`, `recommendedRooms()`, `categories()` as computed signals off the single resource — same external signal surface, so downstream components (`rooms-page.component.ts` etc.) don't change.

### 3.2 Shared categories resource

Given `/rooms/categories` is already server-cached with a 6h TTL, the fix is purely about not re-issuing three independent HTTP calls for identical data across the session. Add one root-provided `CategoriesResource`-style service (or reuse the new page-bundle's `categories()` for the browse page, and give `create-room.service.ts`/`header.component.ts` a single shared `rxResource` instance via a root-provided service) so all three call sites read from one in-flight/cached request instead of three.

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

Add a `fetchedAt: number` alongside each cached entry; on read, treat entries older than a configurable TTL (suggest 5 minutes, matching the room heartbeat cadence's order of magnitude) as stale and refetch. No backend change.

## 4. Files touched

**Backend (`jilalibff`):**
- `src/main/java/com/jilali/room/RoomController.java` — add `/{type}/page-bundle`, `/{type}/search`
- `src/main/java/com/jilali/room/RoomsPageService.java` — new, mirrors `RoomJoinService`
- `src/main/java/com/jilali/room/RoomsSearchService.java` — new
- `src/main/java/com/jilali/room/dto/RoomsPageBundleResponse.java` — new

**Frontend (`JilaliTalk-angular-frontend`):**
- `features/rooms/data/rooms-api.ts` — add `fetchPageBundle()`, `searchRooms()`
- `features/rooms/state/rooms-store.ts`, `live-rooms-store.ts` — collapse 3 resources into 1 bundled resource; swap to `searchRooms()` when searching
- `features/rooms/data/pagination-search.util.ts` — remove `computeIsAutoSearching` once search moves server-side
- `features/rooms/data/rooms-model.ts` — `filterRooms()` keeps category/language filtering (still applied client-side to the already-fetched page for instant UI feedback) but drops responsibility for exhaustive text search
- `core/services/create-room.service.ts`, `core/layout/header.component.ts` — consume the shared categories resource instead of independent fetches
- `features/room/pages/room-page.ts` — `makeVisible()` parallelizes or simplifies per §3.3
- `core/services/user-info.service.ts` — add TTL to cache entries

## 5. Testing / verification

- Backend: unit tests for `RoomsPageService`/`RoomsSearchService` mirroring existing `RoomJoinService` tests (mock `JilaliClient`, assert concurrent fork behavior and merged output).
- `npx tsc --noEmit` clean on the frontend.
- Manual (`/run` or `/verify`): open rooms list, confirm Network tab shows 1 request instead of 3; type a search query for a room on a page not yet loaded, confirm it resolves via 1 request instead of a burst; toggle a minimized room back to visible, confirm no double round-trip in Network tab.

## 6. Out of scope / deferred (needs a separate decision)

- **`PermissionsService` duplicating the role matrix client-side** — not a bug. Jilali/HelloTalk upstream is the actual authority (returns `400`/`100002` on invalid role actions per `livehub_business_rules.md`); the BFF deliberately doesn't own business logic (see `jilalibff/README.md` §"Deliberate decisions"). Client-side duplication here is legitimate UI responsiveness, not drift risk, since it's not a security boundary.
- **DM/notification history has no backend persistence** — `MessagesStore`/`NotificationStore` are localStorage + WebSocket-replay only; no REST history endpoint exists, and none can be proxied from upstream (HelloTalk's IM history isn't in the captured API surface). Fixing this means the BFF taking on its first real datastore, contradicting the "no database" principle in its README. This is a legitimate architecture decision, not a mechanical cleanup — needs its own spec and an explicit call on whether the BFF should grow a database.
- **`userCoins` hardcoded, `GiftsStore` unwired** — incomplete features, not redundant logic. No upstream wallet/coin endpoint has been captured/documented yet; needs traffic-capture research before any BFF work is possible here.
- **`features/profile/`** — unimplemented stub, nothing to consolidate yet.
