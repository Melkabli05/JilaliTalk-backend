# UserController

`src/main/java/com/jilali/user/UserController.java` (136 lines)

## Purpose
REST controller at `/api/users` for room-scoped user actions (join/quit/heartbeat), room user lists, batch status/enrichment, live-record/end-page reads, per-user status, host status, room-scoped profile lookup, and single-user info.

## Responsibilities
- Proxy room membership actions to the upstream via `JilaliGateway.client()`.
- Unwrap the LiveHub `code/msg` envelope with `JilaliResponses.unwrap`.
- Batch-enrich user IDs into `UserInfo` via `gateway::userInfo`.
- Expose the cc2018-decoded room-scoped profile (`/{userId}/profile`) which carries follow relation.

## Public API
- `UserController(JilaliGateway gateway)` — constructor DI.
- `join(cname, busiType)` POST `/rooms/{cname}/join`; `quit(...)` POST; `heartbeat(@Valid HeartbeatRequest)` POST; `roomUsers(@Valid RoomUserListRequest)` POST `/rooms/list`; `batchStatus(@Valid BatchStatusRequest)` POST `/status/batch`; `enrichBatch(@Valid EnrichBatchRequest)` POST `/enrich-batch`; `endPageHost(busiType,cname,contributeListType)` GET; `endPageAudience(busiType,cname)` GET; `recordLive(limit,offset)` GET; `status(userId)` GET `/{userId}/status`; `hostStatus()` GET; `profile(userId,cname,busiType)` GET `/{userId}/profile`; `userInfo(userId)` GET `/info`.

## Dependencies
- Injects only `JilaliGateway`; uses `JilaliClient.JoinQuitRequest`, `JilaliResponses.unwrap`.
- Imports DTOs: `BatchStatusRequest/Response`, `EnrichBatchRequest/Response`, `HeartbeatRequest`, `HostStatus`, `RoomUserListRequest/Response`, `RoomUserProfileResponse`, `UserInfo`, `UserStatus`.
- **Depended on by:** the Angular frontend (HTTP). No server-side callers.

## Coupling and cohesion analysis
Lower coupling than `ProfileController` — a single collaborator (`JilaliGateway`). Cohesion is mixed: it blends room lifecycle (join/quit/heartbeat), room roster reads, and generic user lookups (`/info`, `/{userId}/status`). Cleaner than `ProfileController` because it consistently routes through `gateway.client()` + `JilaliResponses.unwrap`.

## Code smells
- **Repeated unwrap boilerplate**: 9 methods follow `return JilaliResponses.unwrap(gateway.client().xxx(args));` (lines 48-123). Same Shotgun-Surgery risk as `ProfileController`'s pass-throughs.
- **`Map<String,Object>` return types** on `endPageHost`, `endPageAudience`, `recordLive` (lines 93-113) — untyped responses leaked to the frontend (Primitive Obsession); no DTO models these.
- Mild **feature-adjacent grouping**: room actions and generic user lookups in one class.

## Technical debt
- `endPageHost`/`endPageAudience`/`recordLive` return raw maps — should be typed DTOs.
- `enrichBatch` (lines 84-91) does per-ID `gateway::userInfo` in a sequential stream — no concurrency despite the batch intent and despite `ProfileBundleService` demonstrating virtual-thread fan-out; N cold IDs = N sequential encrypted calls.

## Duplicate logic
- The `JilaliResponses.unwrap(gateway.client().x())` shape repeats 9× — matches (conceptually) `ProfileController`'s `profileClient.x()` pass-throughs; both are the "call upstream + unwrap" pattern that appears 20+ times across the two controllers.
- `/info` here (`gateway.userInfo`) and `ProfileBundleService`'s essential `userInfo` fetch call the same gateway method.

## Dead or unused code
None. All methods are `@Get`/`@Post` endpoints invoked reflectively by Micronaut.

## Missing validation / security
- `join`/`quit` validate `cname` with `@NotBlank`; `heartbeat`/`roomUsers`/`batchStatus`/`enrichBatch` use `@Valid`. Good.
- **`endPageHost`/`endPageAudience` (lines 93-106)**: `@QueryValue String cname` has **no `@NotBlank`/validation** before being forwarded into the upstream URL — inconsistent with the `@NotBlank cname` on join/quit.
- `profile(userId, cname, ...)` (line 125): `cname` query param unvalidated before the cc2018 upstream call.
- `status`/`userInfo` take a raw `long userId` with no bounds check (acceptable for single-account BFF).

## Refactoring recommendations
1. Add `@NotBlank` to `cname` on `endPageHost`/`endPageAudience`/`profile` for consistency.
2. Model the three `Map<String,Object>` endpoints as DTOs.
3. Parallelize `enrichBatch` with structured concurrency (reuse `ProfileBundleService`'s pattern) so batch enrichment is actually batched.
4. Consider a shared base/helper for the repeated `unwrap(gateway.client().x())` pattern shared with `ProfileController`.
