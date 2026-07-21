# Class Mapping — Legacy → New

## Controllers

| Legacy | New | Notes |
|---|---|---|
| `room.RoomController` | `roomcontext.api.RoomController` | Shrinks to routing + mapping only; business logic moves to `domain.model.Room` + `application.service.RoomService`. |
| `stage.StageController` | `roomcontext.api.StageController` | Kept separate (see `07-migration-roadmap.md` Phase 2 decision). |
| `manager.ManagerController` | `roomcontext.api.ManagerController` | |
| `comment.CommentController` | `roomcontext.api.CommentController` | |
| `signin.SigninController` | `roomcontext.api.SignInController` | |
| `vip.VipExperienceCardController` | `roomcontext.api.VipController` | |
| `user.ProfileController` + `user.UserController` | `roomcontext.api.ProfileController` + `roomcontext.api.UserController` | Kept as two controllers, matching the legacy split between "own profile" and "other-user lookup" — that split is a real, useful distinction, not incidental duplication. |
| `translate.TranslateController` | `roomcontext.api.TranslateController` | |

## Domain model (the ~90 combined legacy DTOs collapse into 5 aggregates + value objects)

| Legacy file(s) | New domain type | Notes |
|---|---|---|
| `room.dto.HostUser`, `room.dto.RoomUser`, `room.dto.UserBase` | `domain.model.RoomMember` (child entity of `Room.roster`) + `domain.model.valueobject.MicState` | The legacy audit flagged these as a "feature envy" cluster needing composition; verified during the prior refactor pass that `UserBase`'s flat-for-Jackson shape is deliberate (see `docs/audit/reports/duplication.md` item on `HostUser`) — the NEW domain model doesn't need to preserve that wire-flatness constraint internally (only `infrastructure.mapper` does), so `RoomMember` can compose cleanly even though the legacy DTOs couldn't. |
| `room.dto.ChannelListItem`, `ChannelListResponse`, `Channel` | `domain.model.RoomSummary` (one canonical shape) + `application.query.RoomListView` (API-facing projection) | |
| `room.dto.RoomLevelConfigResponse` (incl. nested `RewardItem`) | `domain.model.RoomLevel` value object, reusing `platform.models.RewardItem` (already the canonical shape post-Refactor-3) | |
| `signin.dto.RewardItem` | **deleted** — replaced by the same `platform.models.RewardItem` reuse | |
| `signin.dto.RoomLevelBundleResponse`, `RoomLevelRewardResponse`, `SignItem`, `VoiceSignPanelResponse`, `VoiceTasksResponse` | `domain.model.RoomSignIn` aggregate + its value objects (`SignInDay`, `PendingReward`, `ClaimedReward`) | |
| `comment.dto.Comment` (post-Refactor-5 shape, `CommentDto` already deleted) | `domain.model.Comment` (value object inside `RoomCommentThread`) | Carries the millisecond-timestamp shape forward unchanged — the domain model is a superset-compatible continuation of the already-fixed legacy shape, not a fresh redesign of something already correct. |
| `comment.dto.CaptionEntry`, `CaptionHistoryResponse`, `CaptionSwitchRequest` | `domain.model.Caption` value object + `RoomCommentThread` caption-related methods | |
| `manager.dto.Manager`, `ApproveManagerRequest`, `SetManagerRequest`, `ManagerJudgeResponse`, `ManagerListResponse` | `domain.model.ManagerRoster` (child entity of `Room`) + per-action `Command` records in `application.command.manager` | The audit's retracted "sealed ManagerAction" idea (see `docs/audit/reports/duplication.md` item #8) is replaced by these independent Command records — see `00-architecture-overview.md`'s CQRS section for why this is the correct resolution of the same underlying complaint. |
| `stage.dto.*` (10 files: `DeviceControlRequest`, `KickRequest`, `RaiseHandRequest`, `RaiseHandApprovalRequest`, `StageActionRequest`, `StageInviteRequest`, `StageInviteApprovalRequest`, `StageListResponse`, `StageMember`, `PublisherTokenResponse`) | `domain.model.Stage` (child entity) + per-action `Command` records in `application.command.stage` + `domain.model.valueobject` types for publisher token | Same resolution as manager — independent commands, no forced sealed interface. |
| `vip.dto.*` (11 files) | `domain.model.VipExperienceCard` aggregate (state machine: `UNCLAIMED → CLAIMED → USED`) | The audit's `Card → Detail → Feature → UsedFeature` hierarchy is preserved as-is (validated as "justified decomposition, not duplication" in `docs/audit/packages/vip.md`) — just expressed with real state-transition guards instead of four independent DTOs. |
| `user.dto.*` (36 files, per the audit "collapses to 8-10 shapes") | `domain.model.UserProfile` aggregate + `ProfileSummary`/`PresenceStatus`/`FollowState` value objects, with the 8-10 *response* shapes surviving as `api.dto` projections mapped from the one domain shape | See `01-domain-model.md`'s `UserProfile` section for why the DTO multiplicity was always a wire-format concern, not a missing domain abstraction. |
| `translate.dto.*` (5 files) | Unchanged in spirit — `TranslationRequest`/`TranslationResult` value objects, `AiTranslateUpstreamRequest`/`SseChunk`/`TranslateUpstreamHeaders` move to `infrastructure` as wire-only types | |

## Services

| Legacy | New | Notes |
|---|---|---|
| `room.RoomJoinService` | `application.service.RoomService.joinRoom(...)` | Same `StructuredTaskScope` fan-out pattern, relocated — this is a genuinely good piece of legacy code, not being redesigned, just re-homed. |
| `room.RoomsSearchService`, `room.TextMatcher` | `application.service.RoomSearchService` (query side) | |
| `signin.SigninController.roomLevelBundle`'s inline fan-out | `application.service.SignInService.getRoomLevelBundle(...)` | Extracted from the controller into a proper application service — the legacy version had this logic inline in the controller, which the new layering doesn't allow. |
| `user.ProfileBundleService` | `application.service.UserProfileService.getProfileBundle(...)` | Same fan-out pattern, relocated. |
| `translate.TranslateService` | `domain.service.TranslationService` (interface) + `application.service` orchestration if any request-shaping is needed beyond the domain service call | |
| `room.AgoraTokenCipher` | `domain.service.AgoraTokenService` (or kept as an `infrastructure` crypto utility if it's purely a wire-format concern with no business rule — to be decided in Phase 2 once its actual responsibility is re-examined) | |

## Infrastructure

| Legacy | New | Notes |
|---|---|---|
| `client.JilaliClient` (god interface, ~54 methods) | `infrastructure.client.{Room,Stage,Comment,SignIn,Vip,UserProfile}JilaliClient` | See `06-package-dependency-analysis.md` for why this split doesn't duplicate HTTP configuration. |
| `client.JilaliGateway` | `application.service` helper or `infrastructure.client` shared base — exact home to be decided in Phase 2 depending on whether its `currentUserId()`-style helpers are application-layer concerns (likely) or infra-layer (less likely). | |
| `client.ProfileClient` | `infrastructure.client.ProfileJilaliClient` | Already its own small interface in legacy — minimal change, just relocated. |
| `translate.TranslateClient` / `HtTranslateClient` | `application.port.out.TranslationUpstreamPort` / `infrastructure.client.HtTranslateClient` | Pure move, see `05-port-definitions.md`. |
| `im.ImEventSource` / `realtime.RoomEventSource` (`Sinks.Many` pub-sub) | `infrastructure.websocket.RoomEventTranslator` + Micronaut `ApplicationEventPublisher<RoomEvent>` | New pattern, not a direct move — see `00-architecture-overview.md`'s Micronaut section. |
| `im.dto.ImRealtimeEvent`, `realtime.dto.RoomRealtimeEvent`, `realtime.dto.RoomCcRealtimeEvent` (all sealed, all already well-designed per the audit) | Stay as **wire-level event types**, now explicitly classified as `infrastructure.websocket` concerns rather than living in a `dto` sub-package of a feature — the sealed-interface design itself is unchanged and not being redone, just re-homed and given an explicit conceptual role (wire event vs domain event, see `01-domain-model.md`). | |

## What's explicitly NOT migrated (out of scope, durable instruction)

`auth.*`, `im.*` (the personal-DM channel, not the Room bounded context), `realtime.*`'s connector plumbing (the WebSocket transport mechanics stay as infrastructure primitives reused by the new `infrastructure.websocket` layer, not rewritten), and all 4 CRITICAL security/authorization findings (T-1 through T-4) remain untouched, per the explicit user instruction carried over from the prior refactor goal.
