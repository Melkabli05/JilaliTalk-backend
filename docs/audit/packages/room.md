# `com.jilali.room` — voice-room lifecycle + business logic

## Purpose

The largest feature package. Owns the lifecycle of HelloTalk "voice rooms" (LiveHub channels): creation, joining, ending, search, listing, category/topic browsing, audience reconciliation, and the encrypted Agora-RTC token exchange required to actually connect as a participant.

## File responsibilities (5 root + 23 dto = 28 files)

### Root feature code

| File | One-line summary |
|---|---|
| `AgoraTokenCipher.java` | AES-encrypts/decrypts the RTC token upstream returns, with the session-shared key. |
| `RoomController.java` | The single controller for `/api/room/*`. Largest controller in the codebase — likely a God Class (the agent flagged it as ~15-20+ endpoint methods spanning create/join/end/search/list/batch/category/audience reconciliation). |
| `RoomJoinService.java` | Orchestrates a "join room" call: parallel fan-out using `StructuredTaskScope` (Java 25 preview) to multiple upstream endpoints, with automatic cancellation on any failure. |
| `RoomsSearchService.java` | Search/filter logic (likely backed by `TextMatcher`). |
| `TextMatcher.java` | Tokenized/fuzzy string matching — check for ReDoS risk and algorithmic choice (naive substring vs a smarter method). |

### DTOs (23) — heavy overlap with `realtime` and `vip`

| DTO | Represents |
|---|---|
| `AudienceReconcileResponse` | Roster delta for a rejoin. |
| `BatchChannelStatus`, `BatchQueryRequest`, `BatchQueryResponse` | Bulk channel queries. |
| `Category`, `CategoryTopicListResponse`, `CategoryTopicTag`, `LanguageGroup` | Browse taxonomy. |
| `Channel`, `ChannelListItem`, `ChannelListResponse` | Channel-list payload variants — likely near-duplicates of each other. |
| `CreateVoiceChannelRequest`/`Response`, `EndChannelRequest`, `UpdateVoiceChannelRequest` | Lifecycle mutations. |
| `HostUser`, `RoomUser`, `UserBase` | User-with-role-shape variants — `UserBase` may be intended as a shared base but other records likely redeclare fields instead of composing. |
| `JoinBundleResponse` | Composite "everything you need to display this room" payload. |
| `RoomLevelConfigResponse` | Server-rendered room-level settings. **Cross-package duplicate alert**: similar names exist in `signin.dto.RoomLevelBundleResponse`/`RoomLevelRewardResponse` (confirmed by signin audit: `RewardItem` is an exact field clone). |
| `Topic`, `VoiceRoomInfoObjects`, `VoiceRoomInfoResponse` | Room-detail page payloads. |

## Dependencies

- **Inbound**: every consumer is a REST endpoint from the Angular frontend.
- **Outbound**: depends on `client` (JilaliClient, JilaliGateway), `core`, `crypto` (AgoraTokenCipher), `realtime` (for live state), `stage` (DTOs reused), `user` (DTOs reused), `comment`, and the cross-package `dto` set.
- The `controller`→`service`→`client` chain is layered, but the dto cross-package imports (see `JilaliClient.java` importing from `room/dto/*`) tie input/output types to everywhere they're consumed.

## Improvement opportunities

1. **High — God Class**: `RoomController` carries too many unrelated endpoint groups. Split into `RoomLifecycleController`, `RoomSearchController`, `CategoryTopicController`, `RoomAudienceController`.
2. **High — feature envy in DTO cluster**: `HostUser`, `RoomUser`, `UserBase` should share composition (UserBase as the common record).
3. **High — cross-package duplication**: `RewardItem` in `signin/dto` is exactly the same as the nested `RewardItem` in `room/dto.RoomLevelConfigResponse`. Either lift into a shared location or rename to make the duplication intentional.
4. **Medium**: `RoomJoinService`'s `StructuredTaskScope` use is a good Java 25 preview fit but should be explicitly designed to fail fast on sub-task errors — verify cancellation propagates correctly to all in-flight upstream calls.
5. **Low**: `TextMatcher` should declare its time complexity and be reevaluated on each upstream-input-size change (no known large-data regression here, but worth a perf-test scaffold for future maintainability).
