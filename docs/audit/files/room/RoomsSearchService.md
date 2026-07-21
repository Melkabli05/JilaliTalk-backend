# RoomsSearchService.java

`src/main/java/com/jilali/room/RoomsSearchService.java`

## Purpose
Bounded server-side room search. Upstream LiveHub has no keyword parameter, so this fans out up to `maxPages` concurrent list-room calls, then filters the combined result client-side with `TextMatcher`. Replaces the frontend's up-to-10-sequential-round-trip auto-paginate-while-searching loop with one parallel server-side fan-out.

## Responsibilities
- Dispatch voice vs live listing by `type`.
- Fork `maxPages` paginated list calls concurrently via Structured Concurrency.
- Collect all items and filter them through `matchesQuery`.
- Assemble a fields-of-interest haystack (channel name/cname/description, host nickname, category/topic tag, member nicknames) for matching.

## Public API
- `RoomsSearchService(JilaliClient)` — constructor injection.
- `ChannelListResponse search(String type, String query, int langId, int maxPages)` — the fan-out + filter.
- `static boolean matchesQuery(ChannelListItem item, String query)` — builds the haystack and delegates to `TextMatcher.matches`.
- Constant `PAGE_SIZE = 20`.

## Dependencies
- Injects `JilaliClient`; uses `JilaliResponses`, `ChannelListItem`, `ChannelListResponse`, `TextMatcher`, `StructuredTaskScope`.
- Depended on BY: `RoomController.searchRooms` only.

## Coupling and cohesion analysis
High cohesion — one clear job (paginated fan-out search). Coupling to `ChannelListItem`'s internal shape is notable: `matchesQuery` navigates `item.channel()`, `item.hostUser()`, `item.categoryTopicTag()`, `item.users()` — mild **Feature Envy** on the DTO graph.

## Code smells
- **Feature Envy**: `matchesQuery` (lines 63-81) reaches deep into `ChannelListItem`/`Channel`/`HostUser`/`CategoryTopicTag`/`RoomUser` to pull searchable text. That "what text represents this item" knowledge could live on the DTO (e.g. `ChannelListItem.searchableText()`).
- **Magic behaviour**: `"live".equals(type)` (line 33) — any non-"live" string silently means voice; no validation of `type`.
- Unbounded `maxPages`: no upper clamp here (controller defaults to 5) — a large `maxPages` query param would fan out that many concurrent upstream calls (mild DoS-amplification vector).

## Technical debt
- No clamp/validation on `maxPages` or `type` at this layer.
- Duplicate haystack fields are re-collected per item on every search; fine at current scale.

## Duplicate logic
- Shares the `StructuredTaskScope` open/join/`FailedException`-unwrap idiom with `RoomJoinService` and `ProfileBundleService` — candidate for a shared concurrency helper (not a bug, a DRY opportunity).
- No field-shape duplication.

## Dead or unused code
None. `matchesQuery` is package-private-static and used within `search`; both are reachable from the controller.

## Refactoring recommendations
- Move searchable-text extraction onto `ChannelListItem` (removes Feature Envy, makes matching testable per DTO).
- Validate/clamp `maxPages` and reject unknown `type` explicitly.
- Extract the shared Structured Concurrency scaffolding into a helper reused by `RoomJoinService`.

## Cross-reference
See `TextMatcher.md` (matching algorithm), `RoomJoinService.md` (mirrored concurrency pattern), `dto/ChannelListItem.md`.
