# BFF Consolidation, Round 2 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix four verified frontend/BFF redundancy issues found in a fresh audit — duplicate categories fetching, a sequential room-visibility round trip, an incomplete+wasteful client-side room search, and a `UserInfoService` cache that never refreshes — without touching the areas Round 1 already fixed (join-bundle, enrich-batch, audience-reconcile) or the items explicitly deferred for a separate decision (permissions duplication, DM/notification persistence, coin/gift stubs).

**Architecture:** Extend the exact pattern Round 1 already proved: `StructuredTaskScope`-based fan-out on the BFF for the one genuinely new endpoint (search), plain HTTP response caching (`shareReplay`) on the frontend for categories, and surgical fixes (parallelize two independent calls, add a staleness check) everywhere else. No new architecture, no new dependencies.

**Tech Stack:** Backend: Micronaut 5 / Java 25, virtual threads, `StructuredTaskScope`, JUnit 5 (no Mockito in this project — see Task 2). Frontend: Angular 21 signals, `rxResource`, RxJS, Vitest + `TestBed`.

## Global Constraints

- Backend package root: `com.jilali`. New backend files go under `src/main/java/com/jilali/room/` (search) or `src/main/java/com/jilali/room/dto/` (DTOs), mirroring existing package layout.
- Frontend dependency direction is enforced by ESLint boundaries: `features → store → core → shared`. `shared/` imports nothing else in the app. A shared cross-cutting service (categories) belongs in `shared/data/`, not in `core/services/` or a feature.
- Every Angular component/store change must keep `ChangeDetectionStrategy.OnPush` and avoid manual `.subscribe()` without `takeUntilDestroyed` (this plan doesn't introduce any new manual subscriptions).
- No placeholder code, no TODOs. Every step below has complete, exact code.
- **Automated unit tests are explicitly out of scope for this execution pass, per direct user instruction.** Tasks 1, 3, and 7 originally specified dedicated test files (`TextMatcherTest.java`, `categories.service.spec.ts`, `user-info.service.spec.ts`); those files are **not** to be created. Implement each task's production code directly from the "Write minimal implementation" step's code, skip the "write failing test" / "run test" steps, and verify instead via compilation (`./gradlew compileJava` / `npx tsc --noEmit`) and the task's manual verification step. Do not treat the absence of a test file as a defect in task review for this plan.
- Two separate git repositories: `jilalibff` (backend) and `JilaliTalk-angular-frontend` (frontend), siblings under `/home/mohammed/Desktop/JilaliTalk/`. All file paths below are relative to the named repo. Commit separately in each repo.

---

## Task 1: `TextMatcher` — pure-logic Java port of the frontend's search matching

**Files:**
- Create: `jilalibff/src/main/java/com/jilali/room/TextMatcher.java`

**Interfaces:**
- Produces: `TextMatcher.matches(List<String> haystacks, String query): boolean` — used by Task 2's `RoomsSearchService`.

This is a faithful port of the frontend's `createSearchMatcher` (`JilaliTalk-angular-frontend/src/app/shared/utils/text-search.util.ts:36-52`): case- and accent-insensitive substring matching, every whitespace-separated query token must match at least one haystack field, blank query matches everything.

No dedicated test file for this task — see the Global Constraints note on skipping automated unit tests for this pass. Verify via compilation and Task 2's manual `curl` check, which exercises this logic end-to-end.

- [ ] **Step 1: Write the implementation**

```java
// jilalibff/src/main/java/com/jilali/room/TextMatcher.java
package com.jilali.room;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Java port of the frontend's {@code createSearchMatcher} (text-search.util.ts): case- and
 * accent-insensitive substring matching, tokenized on whitespace. Kept as static pure functions
 * (no Micronaut bean) so it's trivially unit-testable without a mocking framework, mirroring
 * {@code JilaliGatewayTest}'s pattern of testing extracted pure logic directly.
 */
public final class TextMatcher {

    private static final Pattern COMBINING_DIACRITICS = Pattern.compile("\\p{M}");

    private TextMatcher() {
    }

    /** NFD-decompose, strip combining diacritics, lowercase, trim — mirrors normalizeForSearch(). */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return COMBINING_DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase().trim();
    }

    /**
     * True if every whitespace-separated token of {@code query} is a substring of at least one
     * haystack (case/accent-insensitive). A blank query matches everything.
     */
    public static boolean matches(List<String> haystacks, String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        String[] tokens = normalizedQuery.split("\\s+");
        List<String> normalizedHaystacks = haystacks.stream()
                .filter(h -> h != null && !h.isBlank())
                .map(TextMatcher::normalize)
                .toList();
        for (String token : tokens) {
            boolean found = false;
            for (String haystack : normalizedHaystacks) {
                if (haystack.contains(token)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
}
```

- [ ] **Step 2: Compile to verify it builds**

Run: `cd jilalibff && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
cd jilalibff
git add src/main/java/com/jilali/room/TextMatcher.java
git commit -m "feat(room): add TextMatcher, pure-logic port of frontend search matching"
```

---

## Task 2: `RoomsSearchService` + `GET /api/rooms/{type}/search`

**Files:**
- Create: `jilalibff/src/main/java/com/jilali/room/RoomsSearchService.java`
- Modify: `jilalibff/src/main/java/com/jilali/room/RoomController.java`

**Interfaces:**
- Consumes: `TextMatcher.matches(List<String>, String)` (Task 1); `JilaliClient.listVoiceRooms(int, int, int, int)` / `.listLiveRooms(int, int, int, int)` (existing); `JilaliResponses.unwrap(JilaliEnvelope<T>)` (existing).
- Produces: `RoomsSearchService.search(String type, String query, int langId, int maxPages): ChannelListResponse` — consumed by the controller endpoint, and by the frontend in Task 5.

Fans out up to `maxPages` concurrent list-room calls (offsets `0, 20, 40, …`) and filters the combined result with `TextMatcher`. Bounded, not full-corpus — upstream (`GET /channel_list/voice`) has no keyword parameter (confirmed absent from `JilaliClient.java` and every captured request in `websocket_realtime.md`), so this replaces the frontend's up-to-10-sequential-round-trips auto-paginate loop with 1 parallel server-side fan-out, same result ceiling.

No automated test for the orchestration itself, matching existing precedent: `RoomJoinService` (same `StructuredTaskScope` fan-out pattern, already in production since Round 1) has zero tests, because this project has no mocking framework (`build.gradle` lists no Mockito dependency) to fake `JilaliClient`, which is a declarative `@Client` HTTP interface, not something you can trivially hand-instantiate a fake for. Verification for this task is manual (Step 4 below) — matches how `RoomJoinService`/`join-bundle` was verified when it shipped.

- [ ] **Step 1: Write `RoomsSearchService`**

```java
// jilalibff/src/main/java/com/jilali/room/RoomsSearchService.java
package com.jilali.room;

import com.jilali.client.JilaliClient;
import com.jilali.client.JilaliResponses;
import com.jilali.room.dto.ChannelListItem;
import com.jilali.room.dto.ChannelListResponse;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

/**
 * Bounded server-side room search: fans out up to {@code maxPages} concurrent list-room calls
 * and filters the combined result with {@link TextMatcher}. Not a full-corpus search — upstream
 * has no keyword parameter (confirmed absent from {@link JilaliClient} and every captured
 * request in websocket_realtime.md). Replaces the frontend's up-to-10-sequential-round-trips
 * auto-paginate-while-searching loop with one parallel server-side fan-out, same result ceiling.
 * Mirrors {@code RoomJoinService}'s Structured Concurrency pattern.
 */
@Singleton
public class RoomsSearchService {

    private static final int PAGE_SIZE = 20;

    private final JilaliClient client;

    public RoomsSearchService(JilaliClient client) {
        this.client = client;
    }

    public ChannelListResponse search(String type, String query, int langId, int maxPages) {
        boolean isLive = "live".equals(type);
        try (var scope = StructuredTaskScope.open()) {
            var tasks = new ArrayList<StructuredTaskScope.Subtask<ChannelListResponse>>();
            for (int page = 0; page < maxPages; page++) {
                int offset = page * PAGE_SIZE;
                tasks.add(scope.fork(() -> JilaliResponses.unwrap(
                        isLive
                                ? client.listLiveRooms(langId, PAGE_SIZE, offset, 1)
                                : client.listVoiceRooms(langId, PAGE_SIZE, offset, 1))));
            }

            scope.join();

            List<ChannelListItem> matched = new ArrayList<>();
            for (var task : tasks) {
                for (ChannelListItem item : task.get().items()) {
                    if (matchesQuery(item, query)) {
                        matched.add(item);
                    }
                }
            }
            return new ChannelListResponse(matched);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during rooms search", e);
        } catch (StructuredTaskScope.FailedException e) {
            throw new RuntimeException("Upstream fetch failed during rooms search", e.getCause());
        }
    }

    static boolean matchesQuery(ChannelListItem item, String query) {
        List<String> haystacks = new ArrayList<>(List.of(
                item.channel().name(),
                item.channel().cname(),
                item.channel().description() == null ? "" : item.channel().description(),
                item.hostUser().nickname() == null ? "" : item.hostUser().nickname()));
        if (item.categoryTopicTag() != null) {
            haystacks.add(item.categoryTopicTag().categoryName());
            if (item.categoryTopicTag().topicName() != null) {
                haystacks.add(item.categoryTopicTag().topicName());
            }
        }
        if (item.users() != null) {
            for (var user : item.users()) {
                haystacks.add(user.nickname());
            }
        }
        return TextMatcher.matches(haystacks, query);
    }
}
```

- [ ] **Step 2: Add the controller endpoint**

In `jilalibff/src/main/java/com/jilali/room/RoomController.java`:

Add to the constructor and field list (alongside the existing `roomJoinService`, `roomEventSource`):

```java
    private final RoomsSearchService roomsSearchService;

    public RoomController(JilaliClient client, JilaliProperties properties,
                          RoomJoinService roomJoinService, RoomEventSource roomEventSource,
                          RoomsSearchService roomsSearchService) {
        this.client = client;
        this.properties = properties;
        this.roomJoinService = roomJoinService;
        this.roomEventSource = roomEventSource;
        this.roomsSearchService = roomsSearchService;
    }
```

Add the endpoint, placed after `recommendSingleVoiceRoom` (still in the "Discovery" section):

```java
    @Get("/{type}/search")
    public ChannelListResponse searchRooms(
            String type,
            @QueryValue String query,
            @QueryValue(defaultValue = "0") int langId,
            @QueryValue(defaultValue = "5") int maxPages) {
        return roomsSearchService.search(type, query, langId, maxPages);
    }
```

- [ ] **Step 3: Build to verify it compiles**

Run: `cd jilalibff && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification**

Run: `cd jilalibff && ./gradlew run` (or however the project is normally started locally — check `start.sh`), then:

```bash
curl "http://localhost:8080/api/rooms/voice/search?query=music&langId=0&maxPages=2"
```

Expected: HTTP 200, a JSON `{"items": [...]}` body containing only rooms whose name/cname/description/host nickname/category/topic/on-stage-user nicknames match "music" (case/accent-insensitive), pulled from up to 2 pages (40 rooms) of upstream data.

- [ ] **Step 5: Commit**

```bash
cd jilalibff
git add src/main/java/com/jilali/room/RoomsSearchService.java src/main/java/com/jilali/room/RoomController.java
git commit -m "feat(room): add RoomsSearchService + GET /api/rooms/{type}/search"
```

---

## Task 3: `CategoriesService` — shared, deduped categories fetch

**Files:**
- Create: `JilaliTalk-angular-frontend/src/app/shared/data/categories.service.ts`

**Interfaces:**
- Consumes: `Category` (existing, `shared/data/categories.ts`); `API_BASE_URL` token (existing, `core/tokens/api-base-url.token.ts`).
- Produces: `CategoriesService.fetchCategories(busiType?: number): Observable<readonly Category[]>` — consumed by Task 4 (`RoomsApi`, `CreateRoomService`).

Verified two independent call sites hit `GET /rooms/categories` today: `header.component.ts`'s own `rxResource` (via `CreateRoomService.fetchCategories()`) and `RoomsStore`'s own `rxResource` (via `RoomsApi.fetchCategories()`). Fix: one root-provided service in `shared/data/` (the correct layer — both `core/` and `features/rooms/` are allowed to import `shared/`, but not each other) wrapping `HttpClient` with `shareReplay`, keyed by `busiType`, so concurrent or sequential callers coalesce onto one in-flight/cached HTTP call.

No dedicated test file for this task — see the Global Constraints note on skipping automated unit tests for this pass. Verify via typecheck and Task 4's manual verification (Network tab check).

- [ ] **Step 1: Write the implementation**

```typescript
// JilaliTalk-angular-frontend/src/app/shared/data/categories.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { map } from 'rxjs/operators';
import { API_BASE_URL } from '@core/tokens/api-base-url.token';
import { Category } from './categories';

interface CategoryTopicListResponse {
  readonly items: Category[];
}

/** Voice rooms are busiType 2 in LiveHub; live rooms are busiType 1. */
const DEFAULT_BUSI_TYPE = 2;

/**
 * Single shared source for `GET /rooms/categories`, cached per `busiType` for the app's
 * lifetime (the BFF itself already serves this from a 6h `@Cacheable`, so this is purely
 * about not re-issuing redundant HTTP round trips from multiple independent call sites —
 * header.component.ts and RoomsStore each used to fetch this independently).
 */
@Injectable({ providedIn: 'root' })
export class CategoriesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${inject(API_BASE_URL)}/rooms`;
  private readonly cache = new Map<number, Observable<readonly Category[]>>();

  fetchCategories(busiType: number = DEFAULT_BUSI_TYPE): Observable<readonly Category[]> {
    let cached = this.cache.get(busiType);
    if (!cached) {
      cached = this.http
        .get<CategoryTopicListResponse>(`${this.baseUrl}/categories`, {
          params: new HttpParams().set('busiType', busiType),
        })
        .pipe(
          map((res) => res.items),
          shareReplay({ bufferSize: 1, refCount: false }),
        );
      this.cache.set(busiType, cached);
    }
    return cached;
  }
}
```

- [ ] **Step 2: Run typecheck to verify it compiles**

Run: `cd JilaliTalk-angular-frontend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
cd JilaliTalk-angular-frontend
git add src/app/shared/data/categories.service.ts
git commit -m "feat(shared): add CategoriesService, dedupes GET /rooms/categories across callers"
```

---

## Task 4: Wire `CategoriesService` into `RoomsApi` and `CreateRoomService`; dedupe `Category` types

**Files:**
- Modify: `JilaliTalk-angular-frontend/src/app/features/rooms/data/rooms-model.ts`
- Modify: `JilaliTalk-angular-frontend/src/app/features/rooms/data/rooms-api.ts`
- Modify: `JilaliTalk-angular-frontend/src/app/core/services/create-room.service.ts`

**Interfaces:**
- Consumes: `CategoriesService.fetchCategories(busiType?)` (Task 3).
- Produces: no change to `RoomsApi.fetchCategories()` / `CreateRoomService.fetchCategories()` public signatures — `RoomsStore`, `LiveRoomsStore`, `header.component.ts` need no changes for this task.

`rooms-model.ts` and `shared/data/categories.ts` currently declare structurally identical `Category`/`CategoryTopic` interfaces independently. Deleting the duplicate and re-exporting keeps every existing import path (`import { Category } from '../data/rooms-model'`, used by `category-filter.ts` etc.) working unchanged.

- [ ] **Step 1: Dedupe the `Category`/`CategoryTopic` types in `rooms-model.ts`**

In `JilaliTalk-angular-frontend/src/app/features/rooms/data/rooms-model.ts`, replace:

```typescript
export interface CategoryTopic {
  readonly id: number;
  readonly name: string;
  readonly categoryId: number;
}

export interface Category {
  readonly id: number;
  readonly name: string;
  readonly bgColor: string | null;
  readonly fontColor: string | null;
  readonly topics: readonly CategoryTopic[];
}
```

with:

```typescript
export type { Category, CategoryTopic } from '@shared/data/categories';
```

- [ ] **Step 2: Delegate `RoomsApi.fetchCategories()` to `CategoriesService`**

In `JilaliTalk-angular-frontend/src/app/features/rooms/data/rooms-api.ts`, add the import and injected service:

```typescript
import { CategoriesService } from '@shared/data/categories.service';
```

```typescript
@Injectable()
export class RoomsApi {
  private readonly http = inject(HttpClient);
  private readonly categoriesService = inject(CategoriesService);
  private readonly baseUrl = `${inject(API_BASE_URL)}/rooms`;
```

Replace the existing `fetchCategories` body:

```typescript
  fetchCategories(busiType = 2): Observable<readonly Category[]> {
    return this.categoriesService.fetchCategories(busiType);
  }
```

(The `map` import and `HttpParams`-based implementation this replaces are no longer needed for this method, but both stay in the file — `map` and `HttpParams` are still used by other methods in this class.)

- [ ] **Step 3: Delegate `CreateRoomService.fetchCategories()` to `CategoriesService`**

In `JilaliTalk-angular-frontend/src/app/core/services/create-room.service.ts`, add the import and injected service:

```typescript
import { CategoriesService } from '@shared/data/categories.service';
```

```typescript
@Injectable({ providedIn: 'root' })
export class CreateRoomService {
  private readonly http = inject(HttpClient);
  private readonly categoriesService = inject(CategoriesService);
  private readonly baseUrl = `${inject(API_BASE_URL)}/rooms`;
```

Replace the existing `fetchCategories` body:

```typescript
  fetchCategories(busiType = VOICE_BUSI_TYPE): Observable<readonly Category[]> {
    return this.categoriesService.fetchCategories(busiType);
  }
```

The now-unused local `CategoryTopicListResponse` interface and the `map` import in this file can be removed if `map` isn't used elsewhere in it — check with `grep -n "map(" create-room.service.ts` before removing the import; `fetchActiveChannel` doesn't use `map`, so both the local interface and the `map` import from `rxjs/operators` are safe to delete here.

- [ ] **Step 4: Run typecheck**

Run: `cd JilaliTalk-angular-frontend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 5: Run lint (verifies the `@shared/data/categories.service` import doesn't violate dependency boundaries)**

Run: `cd JilaliTalk-angular-frontend && npm run lint`
Expected: no errors — `core → shared` and `features → shared` are both legal edges per the boundaries config.

- [ ] **Step 6: Commit**

```bash
cd JilaliTalk-angular-frontend
git add src/app/features/rooms/data/rooms-model.ts src/app/features/rooms/data/rooms-api.ts src/app/core/services/create-room.service.ts
git commit -m "refactor(rooms): dedupe Category type + route categories fetches through CategoriesService"
```

---

## Task 5: Move room search server-side in `RoomsStore` and `LiveRoomsStore`

**Files:**
- Modify: `JilaliTalk-angular-frontend/src/app/features/rooms/data/rooms-api.ts`
- Modify: `JilaliTalk-angular-frontend/src/app/features/rooms/state/rooms-store.ts`
- Modify: `JilaliTalk-angular-frontend/src/app/features/rooms/state/live-rooms-store.ts`
- Modify: `JilaliTalk-angular-frontend/src/app/features/rooms/data/pagination-search.util.ts`

**Interfaces:**
- Consumes: `GET /api/rooms/{type}/search` (Task 2).
- Produces: `RoomsApi.searchRooms(type, query, langId?, maxPages?): Observable<ChannelListResponse>`, consumed by both stores.

Today, a debounced search with zero local matches triggers `computeIsAutoSearching()` → `loadMore()` in a loop, up to `MAX_SEARCH_OFFSET = 200` rooms (10 sequential `GET` requests). This task replaces that with one call to the new search endpoint whenever a query is active, and disables `loadMore()` while searching (search already returns its full bounded result set in one response — pagination doesn't apply to it). `filterRooms()` (`rooms-model.ts`) is intentionally left untouched: it's idempotent, so re-applying its text filter to already-server-filtered results is harmless, and it's still doing real work for category filtering, which the search endpoint doesn't handle.

- [ ] **Step 1: Add `RoomsApi.searchRooms()`**

In `JilaliTalk-angular-frontend/src/app/features/rooms/data/rooms-api.ts`, add:

```typescript
  searchRooms(
    type: RoomType,
    query: string,
    langId = 0,
    maxPages = 5,
  ): Observable<ChannelListResponse> {
    const params = new HttpParams()
      .set('query', query)
      .set('langId', langId)
      .set('maxPages', maxPages);

    return this.http.get<ChannelListResponse>(`${this.baseUrl}/${type}/search`, { params });
  }
```

- [ ] **Step 2: Rewire `RoomsStore`**

In `JilaliTalk-angular-frontend/src/app/features/rooms/state/rooms-store.ts`:

Add a `MAX_SEARCH_PAGES` constant next to the existing `PAGE_SIZE`/`MAX_SEARCH_OFFSET`:

```typescript
const PAGE_SIZE = 20;
const MAX_SEARCH_PAGES = 5; // 5 × 20 = 100 rooms, same ceiling as the old MAX_SEARCH_OFFSET
```

(`MAX_SEARCH_OFFSET` and its only other use, `computeIsAutoSearching`, are removed in Step 4 below — leave the constant here until then only if still referenced; if not, delete it now.)

Replace the `roomsPage` resource's `stream` (params gains `query`):

```typescript
  private readonly roomsPage = rxResource({
    params: () => ({
      type: this._currentType(),
      offset: this._offset(),
      langId: this._selectedLanguageId() ?? 0,
      query: this.search.debounced(),
    }),
    defaultValue: { items: [] as ChannelListItem[], audienceTotal: 0 } as ChannelListResponse,
    stream: ({ params }) =>
      params.query.trim()
        ? this.api.searchRooms(params.type, params.query, params.langId, MAX_SEARCH_PAGES)
        : this.api.listRooms(params.type, params.langId, PAGE_SIZE, params.offset, 1),
  });
```

Replace `hasMore` (search results aren't paginated, so "load more" never applies while searching):

```typescript
  readonly hasMore = computed(() =>
    this.search.debounced().trim()
      ? false
      : (this.roomsPage.value()?.items.length ?? 0) === PAGE_SIZE,
  );
```

Delete the `isAutoSearching` computed signal entirely (it references `computeIsAutoSearching`, which is being removed in Step 4):

```typescript
  // DELETE:
  readonly isAutoSearching = computed(() =>
    computeIsAutoSearching({
      debouncedQuery: this.search.debounced(),
      filteredCount: this.filteredRooms().length,
      hasMore: this.hasMore(),
      offset: this._offset(),
      maxOffset: MAX_SEARCH_OFFSET,
    }),
  );
```

Update `loadMore()` to no-op while a search query is active:

```typescript
  loadMore(): void {
    if (this.isLoading() || !this.hasMore() || this.search.debounced().trim()) return;
    this._offset.update((o) => o + PAGE_SIZE);
  }
```

Update `setSearchQuery()` to reset pagination offset (so leaving search mode returns to a fresh first page, and `paginateDedup`'s existing `source.offset === 0` reset condition applies cleanly on every query change):

```typescript
  setSearchQuery(query: string): void {
    this._offset.set(0);
    this.search.set(query);
  }
```

Delete the constructor's auto-search effect (it drove `loadMore()` off `isAutoSearching()`, which no longer exists):

```typescript
  // DELETE the whole constructor body's effect():
  constructor() {
    effect(() => {
      if (this.isAutoSearching() && !this.isLoading()) {
        this.loadMore();
      }
    });
  }
```

If nothing else in the constructor needs to run, delete the constructor entirely.

Remove the now-unused `computeIsAutoSearching` import (keep `SearchDebounce`, `paginateDedup`):

```typescript
import { SearchDebounce, paginateDedup } from '../data/pagination-search.util';
```

Remove the `MAX_SEARCH_OFFSET` constant if nothing else in the file references it.

- [ ] **Step 3: Mirror the same change in `LiveRoomsStore`**

Apply the identical transformation to `JilaliTalk-angular-frontend/src/app/features/rooms/state/live-rooms-store.ts` — same `roomsPage`/`hasMore`/`loadMore`/`setSearchQuery` shape, minus the `type` dispatch (always `'live'`):

```typescript
const PAGE_SIZE = 20;
const MAX_SEARCH_PAGES = 5;
```

```typescript
  private readonly roomsPage = rxResource({
    params: () => ({
      offset: this._offset(),
      langId: this._selectedLanguageId() ?? 0,
      query: this.search.debounced(),
    }),
    defaultValue: { items: [] as ChannelListItem[], audienceTotal: 0 } as ChannelListResponse,
    stream: ({ params }) =>
      params.query.trim()
        ? this.api.searchRooms('live' as RoomType, params.query, params.langId, MAX_SEARCH_PAGES)
        : this.api.listLiveRooms(params.langId, PAGE_SIZE, params.offset, 1),
  });
```

(Import `RoomType` from `../data/rooms-model` if not already imported in this file — check first; `RoomsStore` already imports it, `LiveRoomsStore` may not since it hardcodes the live path today.)

Same `hasMore`, `loadMore`, `setSearchQuery`, constructor-effect, and `computeIsAutoSearching`/`MAX_SEARCH_OFFSET`-removal changes as Step 2, applied to this file's `LivePageSource`-based equivalents.

- [ ] **Step 4: Remove `computeIsAutoSearching` from `pagination-search.util.ts`**

In `JilaliTalk-angular-frontend/src/app/features/rooms/data/pagination-search.util.ts`, delete the function (now unused by both stores):

```typescript
  // DELETE:
  export function computeIsAutoSearching(params: {
    readonly debouncedQuery: string;
    readonly filteredCount: number;
    readonly hasMore: boolean;
    readonly offset: number;
    readonly maxOffset: number;
  }): boolean {
    return (
      params.debouncedQuery.trim().length > 0 &&
      params.filteredCount === 0 &&
      params.hasMore &&
      params.offset < params.maxOffset
    );
  }
```

Keep `SearchDebounce` and `paginateDedup` — both still used.

- [ ] **Step 5: Run typecheck and lint**

Run: `cd JilaliTalk-angular-frontend && npx tsc --noEmit && npm run lint`
Expected: no errors. (If `grep -rn "computeIsAutoSearching\|MAX_SEARCH_OFFSET\|isAutoSearching" src/app` still finds references in `voice-list.ts`/`live-list.ts` templates or components after this task, resolve them before proceeding — `voice-list.ts` reads `this.store.isAutoSearching` at `voice-list.ts:83`, so its template usage must be removed too; check `voice-list.html` for a binding to `isAutoSearching` and delete that UI affordance, since search no longer needs a "loading more while searching" indicator — the search request now resolves in one round trip.)

- [ ] **Step 6: Manual verification**

Run: `/run` or start the dev server, open the rooms list, type a search query for a room that would previously require paging past the first 20 results to find. Confirm in the Network tab: exactly one request to `/api/rooms/voice/search`, no burst of sequential `/api/rooms/voice` calls.

- [ ] **Step 7: Commit**

```bash
cd JilaliTalk-angular-frontend
git add src/app/features/rooms/data/rooms-api.ts src/app/features/rooms/state/rooms-store.ts src/app/features/rooms/state/live-rooms-store.ts src/app/features/rooms/data/pagination-search.util.ts src/app/features/rooms/pages/voice-list/voice-list.ts src/app/features/rooms/pages/voice-list/voice-list.html
git commit -m "perf(rooms): move search server-side, replaces up to 10 sequential requests with 1"
```

---

## Task 6: Parallelize `makeVisible()` in `room-page.ts`

**Files:**
- Modify: `JilaliTalk-angular-frontend/src/app/features/room/pages/room-page.ts`

**Interfaces:**
- Consumes: `RoomApi.fetchJoinBundle<T>(cname, busiType): Observable<JoinBundleResponse<T>>` (existing), `RoomApi.joinRoom(cname, busiType): Observable<void>` (existing).

Verified (`room-page.ts:347-381`): `fetchJoinBundle()` and `joinRoom()` have no data dependency on each other — the bundle's fields are only read after both calls resolve, and neither call's input depends on the other's output. They run sequentially today for no reason.

- [ ] **Step 1: Replace the sequential `makeVisible()` body**

In `JilaliTalk-angular-frontend/src/app/features/room/pages/room-page.ts`, replace:

```typescript
  private async makeVisible(cname: string, busiType: number): Promise<void> {
    let voiceInfo: VoiceRoomInfo;
    let stage: StageUsersResponse | undefined;
    let audience: AudienceUsersResponse | undefined;
    try {
      const bundle = await firstValueFrom(this.api.fetchJoinBundle<VoiceRoomInfo>(cname, busiType));
      voiceInfo = bundle.voiceRoomInfo;
      stage = bundle.stageUsers;
      audience = bundle.audienceUsers;
    } catch {
      this.toast.error('Failed to rejoin — room info unavailable');
      return;
    }

    try {
      await firstValueFrom(this.api.joinRoom(cname, busiType));
    } catch {
      this.toast.error('Failed to rejoin visibly');
      return;
    }

    this.roomStore.setVisibility(true);
    this.syncVisibilityToUrl(true);
    this.bffWs.connect(
      cname,
      voiceInfo.hostInfo?.userId ?? 0,
      busiType,
      voiceInfo.configInfo?.heartbeatSecond ?? null,
    );
    this.audienceStore.setCname(cname);
    this.stageStore.reset();
    this.stageStore.updateStageUsers([...(stage?.list ?? [])]);
    this.audienceStore.updateAudienceUsers([...(audience?.list ?? [])]);
    this.toast.success('You are now visible');
  }
```

with:

```typescript
  private async makeVisible(cname: string, busiType: number): Promise<void> {
    const [bundleResult, joinResult] = await Promise.allSettled([
      firstValueFrom(this.api.fetchJoinBundle<VoiceRoomInfo>(cname, busiType)),
      firstValueFrom(this.api.joinRoom(cname, busiType)),
    ]);

    if (bundleResult.status === 'rejected') {
      this.toast.error('Failed to rejoin — room info unavailable');
      return;
    }
    if (joinResult.status === 'rejected') {
      this.toast.error('Failed to rejoin visibly');
      return;
    }

    const { voiceRoomInfo: voiceInfo, stageUsers: stage, audienceUsers: audience } = bundleResult.value;

    this.roomStore.setVisibility(true);
    this.syncVisibilityToUrl(true);
    this.bffWs.connect(
      cname,
      voiceInfo.hostInfo?.userId ?? 0,
      busiType,
      voiceInfo.configInfo?.heartbeatSecond ?? null,
    );
    this.audienceStore.setCname(cname);
    this.stageStore.reset();
    this.stageStore.updateStageUsers([...(stage?.list ?? [])]);
    this.audienceStore.updateAudienceUsers([...(audience?.list ?? [])]);
    this.toast.success('You are now visible');
  }
```

- [ ] **Step 2: Run typecheck**

Run: `cd JilaliTalk-angular-frontend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Manual verification**

Run `/run` or the dev server. Minimize a room, then restore it to visible (the flow that calls `makeVisible`). Confirm in the Network tab that the join-bundle and join requests fire concurrently (overlapping start times), not one after the other. Confirm the room still renders correctly with stage/audience populated.

To verify the error paths still work distinctly, temporarily break one call (e.g. throw in a mocked response) and confirm the correct toast ("room info unavailable" vs. "failed to rejoin visibly") appears for each failure mode — then revert the temporary break.

- [ ] **Step 4: Commit**

```bash
cd JilaliTalk-angular-frontend
git add src/app/features/room/pages/room-page.ts
git commit -m "perf(room): parallelize makeVisible()'s join-bundle + join-room calls"
```

---

## Task 7: `UserInfoService` staleness fix

**Files:**
- Modify: `JilaliTalk-angular-frontend/src/app/core/services/user-info.service.ts`
- Modify: `JilaliTalk-angular-frontend/src/app/shared/ui/user-info-modal/user-info-modal.component.ts`
- Modify: `JilaliTalk-angular-frontend/src/app/features/room/data/ghost-audience.util.ts`
- Modify: `JilaliTalk-angular-frontend/src/app/features/room/feature/moderation/user-action-modal.ts`

**Interfaces:**
- Produces: `UserInfoService.isStale(userId: number): boolean` — new public method, consumed by the three call sites above (plus any others found by the Step 3 grep).

Root cause (verified via grep, not assumed): every call site gates `fetchUserInfo()` behind `if (!getUserInfo(uid))`. Since the cache never expires, once a uid is first seen, `fetchUserInfo` is never called again for it for the rest of the session — VIP status/avatar/nickname changes never surface. The fix stores a `fetchedAt` timestamp per entry and exposes `isStale()`, then each call site's guard becomes `if (!getUserInfo(uid) || isStale(uid))`.

No dedicated test file for this task — see the Global Constraints note on skipping automated unit tests for this pass. Verify via typecheck and the read-through in Step 4.

- [ ] **Step 1: Implement `isStale` in `UserInfoService`**

In `JilaliTalk-angular-frontend/src/app/core/services/user-info.service.ts`, add a TTL constant near the top:

```typescript
const STALE_AFTER_MS = 5 * 60 * 1000; // 5 minutes — matches the room heartbeat cadence's order of magnitude
```

Change the cache's value type to track fetch time. Replace:

```typescript
  private readonly _cache = signal<ReadonlyMap<number, UserInfo>>(new Map());
```

with:

```typescript
  private readonly _cache = signal<ReadonlyMap<number, { readonly info: UserInfo; readonly fetchedAt: number }>>(new Map());
```

Update `getUserInfo` to unwrap the new shape (its own return type is unchanged):

```typescript
  getUserInfo(userId: number): UserInfo | null {
    return this._cache().get(userId)?.info ?? null;
  }
```

Add the new method, placed next to `getUserInfo`:

```typescript
  /** True if `userId` has never been fetched, or its cached entry is older than the TTL. */
  isStale(userId: number): boolean {
    const entry = this._cache().get(userId);
    return !entry || Date.now() - entry.fetchedAt > STALE_AFTER_MS;
  }
```

Update `primeCache` to store the new shape:

```typescript
  primeCache(profiles: readonly UserInfo[]): void {
    if (profiles.length === 0) return;
    const now = Date.now();
    this._cache.update((map) => {
      const next = new Map(map);
      for (const profile of profiles) next.set(profile.userId, { info: profile, fetchedAt: now });
      return next;
    });
  }
```

Update `doFetch` to store the new shape:

```typescript
  private async doFetch(userId: number): Promise<UserInfo | null> {
    this._loading.set(true);
    try {
      const info = await firstValueFrom(
        this.http.get<UserInfo>(`${this.baseUrl}/info`, { params: { userId } }),
      );
      const resolved = info ?? ({} as UserInfo);
      this._cache.update((map) => new Map(map).set(userId, { info: resolved, fetchedAt: Date.now() }));
      return resolved;
    } catch {
      return null;
    } finally {
      this._loading.set(false);
    }
  }
```

- [ ] **Step 2: Find and update every call-site guard**

Run: `cd JilaliTalk-angular-frontend && grep -rn "getUserInfo(" src/app --include="*.ts" | grep -v "user-info.service"`

For each match that guards a `fetchUserInfo` call with `if (!this.userInfoService.getUserInfo(uid))` (or the equivalent local variable name), change it to also check staleness. The three known call sites:

`JilaliTalk-angular-frontend/src/app/shared/ui/user-info-modal/user-info-modal.component.ts:721`, replace:

```typescript
    if (uid > 0 && !this.userInfoService.getUserInfo(uid)) {
```

with:

```typescript
    if (uid > 0 && (!this.userInfoService.getUserInfo(uid) || this.userInfoService.isStale(uid))) {
```

`JilaliTalk-angular-frontend/src/app/features/room/data/ghost-audience.util.ts:50`, replace:

```typescript
    if (uid > 0 && !userInfoService.getUserInfo(uid)) void userInfoService.fetchUserInfo(uid);
```

with:

```typescript
    if (uid > 0 && (!userInfoService.getUserInfo(uid) || userInfoService.isStale(uid))) void userInfoService.fetchUserInfo(uid);
```

`JilaliTalk-angular-frontend/src/app/features/room/feature/moderation/user-action-modal.ts:798`, replace:

```typescript
    if (uid && !this.userInfoService.getUserInfo(uid)) {
```

with:

```typescript
    if (uid && (!this.userInfoService.getUserInfo(uid) || this.userInfoService.isStale(uid))) {
```

If the Step 2 grep finds any additional `if (!getUserInfo(...))`-shaped guard beyond these three, apply the same `|| isStale(...)` addition to it.

Do not change `comments-store.ts:86` or `room-page-base.ts:406` — these only *read* `getUserInfo()` for display, they don't gate a `fetchUserInfo()` call, so there's no staleness decision to make there.

- [ ] **Step 3: Run typecheck**

Run: `cd JilaliTalk-angular-frontend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 4: Manual verification**

Read-through: confirm all three call sites were updated (`grep -n "isStale" src/app -r` should show exactly 4 matches: the service definition + 3 call sites, unless Step 2 found more). Open the user-info modal for a user, close it, wait, reopen — confirm no crash and the profile still renders (functional smoke test of the new cache shape).

- [ ] **Step 5: Commit**

```bash
cd JilaliTalk-angular-frontend
git add src/app/core/services/user-info.service.ts src/app/shared/ui/user-info-modal/user-info-modal.component.ts src/app/features/room/data/ghost-audience.util.ts src/app/features/room/feature/moderation/user-action-modal.ts
git commit -m "fix(user-info): add 5-minute staleness check, previously cached forever per session"
```

---

## Final verification (after all tasks)

This plan adds no new automated tests (see Global Constraints); the commands below run the pre-existing suites as a regression check, not to validate new coverage.

- [ ] Backend: `cd jilalibff && ./gradlew build` — clean build, existing tests still pass.
- [ ] Frontend: `cd JilaliTalk-angular-frontend && npx tsc --noEmit && npm run lint && npx vitest run` — no errors, existing tests still pass.
- [ ] Frontend: `npm run verify` (boundaries, cycles, dependency graph) — clean.
- [ ] Manual, per the spec's §5: open rooms list → create-room modal, confirm exactly one `/rooms/categories` request for the whole session; search for a room past the first page, confirm one request; minimize/restore a room, confirm the two `makeVisible` requests overlap in the Network tab.
