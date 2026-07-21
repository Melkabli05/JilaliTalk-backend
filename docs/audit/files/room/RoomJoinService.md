# RoomJoinService.java

`src/main/java/com/jilali/room/RoomJoinService.java`

## Purpose
Assembles the "join a room" payload by fanning the four upstream LiveHub calls (room info, stage roster, audience roster, comment history) out concurrently on virtual threads via Structured Concurrency, so the browser makes one round trip instead of four. Backs `GET /api/rooms/{cname}/join-bundle`.

## Responsibilities
- Fetch room info first (deliberately sequenced before the other three — upstream requires it), then fork stage/audience/comments concurrently.
- Retry upstream 5xx a bounded number of times (`withUpstreamRetry`) to tolerate fresh-room propagation lag.
- Decrypt the RTC token in the returned room info.
- Convert upstream `Comment` (Unix seconds) into `CommentDto` (milliseconds).
- Rethrow `HttpClientResponseException` unwrapped so the global handler can log upstream's body.

## Public API
- `RoomJoinService(JilaliClient, JilaliProperties)` — constructor injection.
- `JoinBundleResponse joinBundle(String cname, int busiType)` — the bundle fan-out; `busiType` 1=live, 2=voice.
- `<T> T withUpstreamRetry(Callable<T>) throws Exception` — package-private; also reused by `RoomController` info endpoints.
- Private: `decryptRtcToken`, `toCommentDto`, `toMsgDto`, `toReplyInfoDto`.
- Constants: `MAX_UPSTREAM_ATTEMPTS=4`, `UPSTREAM_RETRY_DELAY=700ms`.

## Dependencies
- Injects `JilaliClient`, `JilaliProperties`.
- DTOs across packages: `comment.dto.*`, `stage.dto.StageListResponse`, `user.dto.RoomUserList*`, `room.dto.JoinBundleResponse`, `room.dto.VoiceRoomInfoResponse`.
- Uses `java.util.concurrent.StructuredTaskScope` (finalized Java 25), `AgoraTokenCipher`.
- Depended on BY: `RoomController` (join-bundle + retry adapter); its pattern is mirrored/cited by `signin.dto.RoomLevelBundleResponse`, `user.ProfileBundleService`, `SigninController`.

## Coupling and cohesion analysis
Reasonably cohesive around "build the join bundle". Some coupling breadth: it reaches into `comment`, `stage`, `user` DTO packages to do timestamp/shape conversion (`toCommentDto` etc.), which is mild **Feature Envy** toward the comment package. The retry helper is a cross-cutting concern awkwardly parked here (it serves the controller too).

## Code smells
- **Feature Envy**: `toCommentDto` (lines 196-205) hand-maps 27 fields of a `comment` DTO; that mapping arguably belongs in the `comment` package.
- **Long parameter mapping / Long Method**: `toCommentDto` is a 27-argument constructor call — brittle, positional, easy to misorder.
- **Misplaced shared utility**: `withUpstreamRetry` is package-private purely so the controller can borrow it — a retry policy is a cross-cutting concern that should be its own component.
- **Duplicate `decryptRtcToken`** with `RoomController` (see below).

## Technical debt
- The 27-positional-argument `CommentDto` construction (line 197) will break silently if `CommentDto` field order changes.
- Retry uses `Thread.sleep` on a blocking virtual thread (fine here, but ties timing into the method).
- `decryptRtcToken` divergence: here it logs+returns on null rtcInfo; the controller copy throws BAD_GATEWAY — same operation, two behaviours.

## Duplicate logic
- `decryptRtcToken` (lines 182-193) duplicates `RoomController.decryptRtcToken` (lines 226-238) almost verbatim (key fetch + `AgoraTokenCipher.decrypt` + `withRtcToken`). Consolidate.
- The Structured Concurrency fan-out shape is deliberately mirrored by `RoomsSearchService` and `ProfileBundleService` — a shared pattern rather than copy-paste, but a `StructuredScopeHelper` could DRY the try/catch (`InterruptedException` + `FailedException` unwrap) that repeats across all three.

## Dead or unused code
None. `withUpstreamRetry` is used by both this class and `RoomController`; all private mappers are called from `joinBundle`.

## Refactoring recommendations
- Extract `withUpstreamRetry` into an injectable `UpstreamRetryPolicy` bean shared by controller and service.
- Move `Comment`→`CommentDto` mapping into a `CommentMapper` in the `comment` package (removes Feature Envy and the 27-arg call site).
- Extract the single canonical `decryptRtcToken`.
- Consider a small helper wrapping the repeated `StructuredTaskScope` join/catch idiom shared with `RoomsSearchService`.

## Cross-reference
See `RoomController.md` (duplicate decrypt, retry adapter), `RoomsSearchService.md` (mirrored concurrency pattern), `AgoraTokenCipher.md`.
