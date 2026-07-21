# ProfileController

`src/main/java/com/jilali/user/ProfileController.java` (245 lines)

## Purpose
REST controller at `/api/profile` exposing the profile-page surface: own profile (`/me`, `/stats`, `/limitations`, `/edit`, `/increment`), follow graph (`/followers`, `/following`, `/follow`, `/unfollow`), engagement (`/like-count`, `/visitors`, `/visit`), lookups (`/langs`, `/tags`, `/pay-chat-info`, `/reminder-moment`, `/blocklist`), and the aggregated `/{userId}/bundle`.

## Responsibilities
- Thin pass-through from HTTP to `ProfileClient` for most endpoints.
- Normalize `/unfollow`'s divergent upstream shape into the shared `FollowResultResponse`.
- Build the signed `/visitors` request server-side (device metadata + MD5 sign) from BFF identity.
- Coerce loosely-typed `/visit` map fields into a typed `VisitBody`.
- Delegate `/{userId}/bundle` to `ProfileBundleService`.

## Public API
- Constructor injecting `ProfileClient`, `ProfileBundleService`, `JilaliProperties`, `AuthTokenHolder`, `ObjectMapper`.
- `private long callerUserId()` — re-derives caller uid from the live auth token via `UidExtractor`.
- `me()` GET `/me`; `followers(lang,pageIndex,pageSize)` GET; `following(lang,focusTab,pageSize,title)` GET; `follow(FollowRequest)` POST; `unfollow(UnfollowRequest)` POST (normalizes); `visit(Map)` POST; `likeCount(lang,terminalType,uid)` GET; `langs(userId)` GET; `stats(Map)` POST; `visitors(VisitorHistoryRequest)` POST (re-signs); `edit(ProfileEditRequest)` POST; `limitations()` GET; `increment(lang,version)` GET; `payChatInfo(toId)` GET; `reminderMoment(to)` GET; `blocklist()` GET; `tags(lang,version)` GET; `bundle(userId)` GET `/{userId}/bundle`.
- `private static long toLong(Object)` / `int toInt(Object)` — numeric coercion helpers.

## Dependencies
- Injects `ProfileClient`, `ProfileBundleService`, `JilaliProperties`, `AuthTokenHolder`, `ObjectMapper`; uses `UidExtractor`, `Md5Util`, `ApkSignatureGenerator`, `ProfileClient.*Body` nested request types.
- Imports 18 DTOs from `com.jilali.user.dto`.
- **Depended on by:** the Angular frontend (HTTP). No server-side callers (endpoint methods invoked reflectively by Micronaut).

## Coupling and cohesion analysis
Cohesion is only moderate — it is a broad "everything profile-ish" facade spanning follow graph, edit, visitors, likes, tags, and bundle. Coupling is high: 5 injected collaborators plus ~18 DTOs and several `ProfileClient.*Body` inner types. Most methods are one-liners delegating to `profileClient`, so the class is largely a routing table.

## Code smells
- **Repeated delegation boilerplate (pass-through pattern)**: ~13 methods are `return profileClient.xxx(args);` — a Shotgun-Surgery risk (adding a cross-cutting concern like validation/metrics means touching every method).
- **Primitive Obsession / stringly-typed `visit(Map<String,Object>)`**: lines 118-137 hand-parse an untyped map with `toLong`/`toInt` casts and `(String)` casts — despite a typed `VisitRequest` DTO existing and being unused.
- **Feature Envy**: the `/unfollow` normalization (lines 108-116) reaches into `result.data().listTimestamp()` and rebuilds a `FollowResultData` — mapping logic that belongs in a mapper/DTO factory, not the controller.
- **Long Method**: `visit` (~20 lines of manual coercion) and `visitors` (manual signed-body construction) are the two heaviest methods.

## Technical debt
- `/visit` should bind `@Body VisitRequest` (or a proper signed body) instead of `Map<String,Object>`; the manual `toLong`/`toInt` helpers are debt.
- Inconsistent envelope handling: some responses use `status/message`, others `code/msg` — the controller leaks upstream envelope inconsistency to the frontend.
- `stats(Map<String,Object>)` (line 153) also passes an untyped map straight through.

## Duplicate logic
- The `toLong`/`toInt` coercion helpers duplicate the same idea likely present in other map-consuming controllers.
- The pass-through delegation shape repeats 13× and is the prime extraction candidate (see package doc).
- `/pay-chat-info`, `/reminder-moment`, `/limitations`, `/stats` here duplicate the exact upstream calls `ProfileBundleService` makes — the bundle and the individual endpoints call the same `profileClient` methods.

## Dead or unused code
No dead methods (all `@Get`/`@Post` are framework-invoked). Note: the typed `VisitRequest` DTO that *would* back `/visit` is unused because this controller chose `Map` — dead DTO tracked in `VisitRequest.md`.

## Missing validation / security
- **`/visit` (line 119)**: `@Body Map<String,Object>` is completely unvalidated — `uid`, `visitor_uid`, `sign` are passed through untyped; `Long.parseLong`/`Integer.parseInt` on attacker-controlled strings can throw uncaught `NumberFormatException` (line 238/244).
- **`/stats` (line 153)**: untyped `Map` body forwarded verbatim to upstream with no validation.
- Query-param endpoints (`likeCount`, `langs`, `payChatInfo`, `reminderMoment`, `bundle`) take a raw `long uid`/`userId` with no bounds/ownership check — acceptable for a single-account BFF but worth noting the target uid is unvalidated before building the upstream call.

## Refactoring recommendations
1. Extract the pass-through methods; or accept them but add validation/metrics via a filter rather than per-method.
2. Replace `visit(Map)` and `stats(Map)` with typed, `@Valid` request records (revive `VisitRequest`).
3. Move `/unfollow` normalization into a `FollowResultResponse.fromUnfollow(...)` factory.
4. Consider splitting the follow-graph endpoints into their own controller to raise cohesion (see package doc's ProfileController-vs-UserController analysis).
