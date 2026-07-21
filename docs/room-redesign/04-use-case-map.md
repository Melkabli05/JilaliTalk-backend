# Use-Case Map — Legacy Endpoint → New Command/Query

Grep-verified against the actual legacy controllers (`RoomController`, `StageController`, `ManagerController`, `CommentController`, `SigninController`, `ProfileController`, `UserController`, `VipExperienceCardController`, `TranslateController`) — 83 endpoints total, all accounted for below (see the Coverage Check at the end of this document for the per-controller count). Every row keeps the same HTTP method + path (see `10-compatibility-considerations.md` — the wire contract does not change) and maps to exactly one new `Command` or `Query` type.

## Room lifecycle & discovery (21 legacy endpoints, `room.RoomController`)

| Legacy endpoint | New use case | Type |
|---|---|---|
| `GET /voice` | `ListVoiceRoomsQuery` | Query |
| `GET /live` | `ListLiveRoomsQuery` | Query |
| `GET /voice/recommend` | `RecommendVoiceRoomsQuery` | Query |
| `GET /live/recommend` | `RecommendLiveRoomsQuery` | Query |
| `GET /voice/recommend-single` | `RecommendSingleVoiceRoomQuery` | Query |
| `GET /{type}/search` | `SearchRoomsQuery` | Query |
| `GET /language-groups/voice` | `ListVoiceLanguageGroupsQuery` | Query |
| `GET /language-groups/live` | `ListLiveLanguageGroupsQuery` | Query |
| `GET /categories` | `ListCategoriesQuery` | Query |
| `GET /voice/{cname}` | `GetVoiceRoomInfoQuery` | Query |
| `GET /live/{cname}` | `GetLiveRoomInfoQuery` | Query |
| `GET /{cname}/join-bundle` | `JoinRoomCommand` (fan-out: room info + stage list + roster + comments, `StructuredTaskScope`, mirrors legacy `RoomJoinService`) | Command* |
| `GET /{cname}/audience-reconcile` | `ReconcileAudienceQuery` | Query |
| `GET /{cname}/basic` | `GetRoomBasicInfoQuery` | Query |
| `POST /batch-query` | `BatchQueryRoomsQuery` | Query |
| `GET /config` | `GetRoomLevelConfigQuery` | Query |
| `POST /voice` | `CreateVoiceRoomCommand` | Command |
| `POST /voice/update` | `UpdateVoiceRoomCommand` | Command |
| `POST /end` | `EndRoomCommand` — calls `Room.end(hostId)` | Command |
| `GET /active` | `GetActiveRoomQuery` | Query |
| `GET /latest-settings` | `GetLatestRoomSettingsQuery` | Query |

\* `JoinRoomCommand` is modeled as a Command (not a Query) even though it's a `GET` in the legacy wire contract — joining has a real side effect (the caller becomes tracked as a room member upstream) even though the HTTP verb predates this redesign and is kept as `GET` for wire compatibility (see `10-compatibility-considerations.md`). The **use case classification is about business semantics, not HTTP verb** — a `GET` endpoint can still be a Command internally.

## Stage mechanics (10 legacy endpoints, `stage.StageController`)

| Legacy endpoint | New use case | Type |
|---|---|---|
| `GET /list` | `ListStageOccupantsQuery` | Query |
| `POST /join` | `JoinStageCommand` — `Room.assignStageSeat(...)` | Command |
| `POST /quit` | `QuitStageCommand` — `Room.vacateStageSeat(...)` | Command |
| `POST /raise-hand` | `RaiseHandCommand` — `Room.queueRaiseHand(...)` | Command |
| `POST /kick` | `KickFromStageCommand` — `Room.kick(...)` | Command |
| `POST /raise-hand/approval` | `ApproveRaiseHandCommand` — `Room.approveRaiseHand(...)` | Command |
| `POST /invite` | `InviteToStageCommand` | Command |
| `POST /invite/approval` | `ApproveStageInviteCommand` | Command |
| `POST /device-control` | `ControlStageDeviceCommand` | Command |
| `GET /publisher-token` | `GetPublisherTokenQuery` | Query |

## Manager (moderator) role (4 legacy endpoints, `manager.ManagerController`)

| Legacy endpoint | New use case | Type |
|---|---|---|
| `GET` (list) | `ListManagersQuery` | Query |
| `POST` (set) | `SetManagerCommand` — `Room.grantManager`/`revokeManager` | Command |
| `POST /approve` | `ApproveManagerOperationCommand` | Command |
| `GET /judge` | `JudgeManagerCandidacyQuery` | Query |

## Comments & captions (4 legacy endpoints, `comment.CommentController`)

| Legacy endpoint | New use case | Type |
|---|---|---|
| `GET /captions/history` | `GetCaptionHistoryQuery` | Query |
| `POST /captions/switch` | `SwitchCaptionLanguageCommand` | Command |
| `GET /comments` | `ListCommentsQuery` | Query |
| `POST /comments` | `PostCommentCommand` — `RoomCommentThread.post(...)` | Command |

## Sign-in / daily rewards (7 legacy endpoints, `signin.SigninController`)

| Legacy endpoint | New use case | Type |
|---|---|---|
| `GET /panel` | `GetSignInPanelQuery` | Query |
| `GET /tasks` | `ListVoiceTasksQuery` | Query |
| `GET /room-level-reward` | `GetRoomLevelRewardQuery` | Query |
| `POST /room-level-reward` | `ClaimRoomLevelRewardCommand` — `RoomSignIn.claim(...)` | Command |
| `POST /task-reward` | `ClaimTaskRewardCommand` — `RoomSignIn.claimTask(...)` | Command |
| `GET /room-level-config` | `GetRoomLevelConfigQuery` (shared with room's `/config` — see `08-class-mapping.md` note on consolidating these two legacy near-duplicate endpoints) | Query |
| `GET /room-level-bundle` | `GetRoomLevelBundleQuery` (fan-out, `StructuredTaskScope`, mirrors legacy `SigninController.roomLevelBundle`) | Query |

## VIP experience cards (5 legacy endpoints, `vip.VipExperienceCardController`)

| Legacy endpoint | New use case | Type |
|---|---|---|
| `GET /feature-right` | `CheckFeatureRightQuery` | Query |
| `GET /records` | `ListVipCardRecordsQuery` | Query |
| `POST /use` | `UseVipCardCommand` — `VipExperienceCard.use(...)` | Command |
| `POST /receive-friend-card` | `ReceiveFriendCardCommand` — `VipExperienceCard.receiveFromFriend(...)` | Command |
| `POST /claim-trial` | `ClaimVipTrialCommand` — `VipExperienceCard.claim(...)` | Command |

## User profile & presence (31 legacy endpoints across `user.ProfileController` + `user.UserController`)

| Legacy endpoint | New use case | Type |
|---|---|---|
| `GET /me` | `GetOwnProfileQuery` | Query |
| `GET /followers` | `ListFollowersQuery` | Query |
| `GET /following` | `ListFollowingQuery` | Query |
| `POST /follow` | `FollowUserCommand` — `UserProfile.follow()` | Command |
| `POST /unfollow` | `UnfollowUserCommand` — `UserProfile.unfollow()` | Command |
| `POST /visit` | `RecordProfileVisitCommand` | Command |
| `GET /like-count` | `GetLikeCountQuery` | Query |
| `GET /langs` | `GetUserLanguagesQuery` | Query |
| `POST /stats` | `GetOwnStatsQuery` (POST verb kept for wire compat; read-only) | Query |
| `POST /visitors` | `ListProfileVisitorsQuery` (POST verb kept; read-only) | Query |
| `POST /edit` | `EditProfileCommand` | Command |
| `GET /limitations` | `GetProfileLimitationsQuery` | Query |
| `GET /increment` | `GetProfileIncrementQuery` | Query |
| `GET /pay-chat-info` | `GetPayChatInfoQuery` | Query |
| `GET /reminder-moment` | `GetReminderMomentQuery` | Query |
| `GET /blocklist` | `GetBlockListQuery` | Query |
| `GET /tags` | `GetUserTagsQuery` | Query |
| `GET /{userId}/bundle` | `GetProfileBundleQuery` (fan-out, `StructuredTaskScope`, mirrors legacy `ProfileBundleService`) | Query |
| `POST /rooms/{cname}/join` | maps to `JoinRoomCommand` (same use case as room's join-bundle — see `08-class-mapping.md`, these two legacy endpoints likely overlap and should be reconciled during Phase 3, not blindly duplicated) | Command |
| `POST /rooms/{cname}/quit` | `QuitRoomCommand` | Command |
| `POST /heartbeat` | `SendHeartbeatCommand` | Command |
| `POST /rooms/list` | `ListJoinedRoomsQuery` | Query |
| `POST /status/batch` | `GetBatchUserStatusQuery` | Query |
| `POST /enrich-batch` | `EnrichUserBatchQuery` | Query |
| `GET /end-page/host` | `GetHostEndPageQuery` | Query |
| `GET /end-page/audience` | `GetAudienceEndPageQuery` | Query |
| `GET /record/live` | `GetLiveRecordQuery` | Query |
| `GET /{userId}/status` | `GetUserStatusQuery` | Query |
| `GET /host-status` | `GetHostStatusQuery` | Query |
| `GET /{userId}/profile` | `GetRoomUserProfileQuery` | Query |
| `GET /info` | `GetUserInfoQuery` | Query |

## Translation (1 legacy endpoint, `translate.TranslateController`)

| Legacy endpoint | New use case | Type |
|---|---|---|
| `POST /translate` | `TranslateTextCommand` — calls `domain.service.TranslationService` directly, no aggregate involved (see `01-domain-model.md`) | Neither (plain service call, deliberately not forced into Command/Query) |

## Coverage check

21 (room) + 10 (stage) + 4 (manager) + 4 (comment) + 7 (signin) + 5 (vip) + 31 (user, `ProfileController` 18 + `UserController` 13) + 1 (translate) = **83 endpoints, all accounted for above.** Two pairs are flagged as likely the SAME underlying use case reachable via two legacy paths (room's `/config` vs signin's `/room-level-config`; room's `/{cname}/join-bundle` vs user's `/rooms/{cname}/join`) — a real duplication finding worth resolving during Phase 3 (one canonical use-case implementation, two thin route registrations if both paths must stay live for wire compatibility), not grounds for building the same behavior twice.
