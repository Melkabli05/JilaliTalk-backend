## Purpose

Outbound response DTO for the `POST /api/profile/visitors` endpoint. Wraps a paged list of "who visited my profile" summaries.

## Public API

- `record VisitorsResponse(boolean hasMore, int oldestId, List<VisitItem> items)` — `hasMore` flags whether the upstream response indicated more pages, `oldestId` is the cursor for the next page, `items` is the page.

The `VisitItem` inner record holds one visitor: typically `{userId, nickname, headUrl, visitedAt, isVip, ...}` (per upstream wire shape — the actual fields are documented in the per-file doc if the agent captured them; otherwise inferred from the scriptv2.js reference).

## Dependencies

- Outbound: returned by `user/ProfileController#visitors()` to the Angular frontend.
- Inbound: constructed in `user/ProfileController#visitors()` from the upstream `VisitorsResponse` (different class, in `client.JilaliClient.visitors()` or similar — verify the wire-DTO mapping is at the boundary).
- Cross-references: `user/dto/VisitRequest` is **dead** (per the per-package audit), but `VisitItem` (inside this DTO) is alive and used.

## Coupling and cohesion

Single shape, well-isolated. Cross-package DTO import is via `user/dto` only, not from other features.

## Code smells

- The inner `VisitItem` record is a near-clone of `user/dto/UserInfo` (same field set: `userId`, `nickname`, `headUrl`, presence fields). Verify if there is a meaningful difference; if not, this is a consolidation candidate with `UserInfo`.
- `hasMore: boolean` and `oldestId: int` are the pagination cursor pair. Other DTOs in this family use `String` cursor — slight inconsistency.

## Technical debt

- Pagination-cursor shape (`boolean hasMore` + `int oldestId`) is bespoke; other DTOs use string cursors. A unified pagination wrapper record (e.g. `Page<T>` with `nextCursor: T`) would consolidate.

## Duplicate logic

- `VisitItem` ↔ `UserInfo` near-duplicate (verify and consolidate in Phase 4).

## Dead or unused code

None — actively returned by `ProfileController.visitors()`.

## Java 25 modernization opportunities

- `boolean hasMore` → could be a sealed `Pagination` enum (`SinglePage | LastPage | HasMorePage`) for richer semantics, but the current boolean is fine.
- The record pair (`hasMore`, `oldestId`) is a candidate for a parameterized `Page<T>` wrapper.

## Micronaut built-in opportunities

- None directly — but if the rewrite moves to a `Page<T>` wrapper, consider a `Pageable` argument-driven controller method (`@Get(uri = "/visitors") page: int`).

## Refactoring recommendations

1. **Medium**: introduce a shared `Page<T>` wrapper used by all paged responses (currently 3-4 different cursor shapes across `comment/dto`, `room/dto`, `user/dto`).
2. **Medium**: `VisitItem` ↔ `UserInfo` consolidation (Phase 4).
3. **Low**: rename `oldestId` → `nextCursor` (idiomatic) and consider making it `String` for forward-compatibility.
