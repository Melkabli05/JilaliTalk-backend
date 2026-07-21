# Duplication Report

> Concrete duplication findings across `jilalibff`. Each row links to the per-package or per-file docs that contain the field-by-field evidence.

## Severity scale

- **High**: very large code surface, breaks architectural layering, or is paid forward into every new feature.
- **Medium**: isolated to two files but mechanically consolidatable.
- **Low**: cosmetic or trivial.

## 1. Two parallel "upstream WebSocket connector + event source + browser relay" packages (HIGH)

**`com.jilali.im` vs `com.jilali.realtime`** independently implement the same pattern with binary vs JSON wire formats and singleton vs per-`cname` cardinality.

| Concern | `im/` | `realtime/` |
|---|---|---|
| Connector | `HtImUpstreamConnector` | `HtLiveHubUpstreamConnector` |
| Notify mapper | `HtImNotifyMapper` | `HtNotifyMapper`, `HtCcNotifyMapper` |
| Pub-sub source | `ImEventSource` | `RoomEventSource` |
| Browser WS relay | `ImSocketController` | `RoomSocketController` |
| Event union | `ImRealtimeEvent` (sealed) | `RoomRealtimeEvent`, `RoomCcRealtimeEvent` (sealed) |

**Estimated duplication**: ~300-500 lines could collapse into a shared `UpstreamWebSocketConnector<TEvent>` once the wire-format specifics (binary vs JSON) are encapsulated and the cardinality (singleton vs per-key) is parameterized.

**Concrete fix**: see target-structure + roadmap reports.

**Status (Refactor 6)**: Started the consolidation incrementally. The state-machine + reconnect-scheduler surface has been extracted into `com.jilali.platform.websocket.WebSocketConnectionLifecycle` (single source of truth for both connectors' `intentionalClose` flag, `reconnectInBackground` loop, and `close` semantics). Each connector still owns its wire-specific code (frame encode/decode, notify mapping, event dispatch) and delegates to the lifecycle for the shared parts. Refactor 7 will complete the migration by removing the connectors' own duplicated state fields.

## 2. `client/JilaliClient` imports from 7 feature-DTO packages (HIGH — circular dep)

See the dependency-analysis report. `JilaliClient.java` alone imports ~65 DTO classes owned by `comment.dto`, `manager.dto`, `room.dto`, `signin.dto`, `stage.dto`, `user.dto`. Not duplication per se, but the cause is the same pattern repeated.

## 3. `comment/dto/Comment` ↔ `comment/dto/CommentDto` (HIGH)

28 fields, near-exact duplicates. Two hand-written mappers in `CommentController.toDto` AND `RoomJoinService.toCommentDto` that do identical work (sec→ms timestamp conversion + a casing rename).

**Fix**: single record + a custom Micronaut Serde timestamp serializer.

**Status**: ✅ consolidated in Refactor 5 (2026-07-21). The single canonical record at `com.jilali.comment.dto.Comment` now exposes timestamps in epoch milliseconds (the unit the Angular frontend wants) with a static `Comment.fromWireSeconds(Comment)` factory that re-scales the upstream's Unix-seconds values during deserialization. `CommentDto` deleted, both hand-written mappers deleted (~30 redundant lines removed). Behavior preserved: the wire still carries `created_at` / `updated_at` in seconds, the consumer still receives them in milliseconds — the conversion is just at the single record's static factory now.

## 4. `signin/dto/RewardItem` ↔ `room/dto/RoomLevelConfigResponse.RewardItem` (MEDIUM)

Exact 8-field clone, only `multi_name` nullability differs.

**Fix**: lift to a shared `com.jilali.platform.models.RewardItem`.

**Status**: ✅ consolidated in Refactor 3 (2026-07-21). The single canonical record now lives at `com.jilali.platform.models.RewardItem`. Both `signin.dto.RoomLevelRewardResponse` and `room.dto.RoomLevelConfigResponse` reference it. `~30` redundant lines removed.

## 5. Status triplet in `user/dto/` (MEDIUM)

`UserOnlineStatus` / `HostStatus` / `UserStatus` — three separate record DTOs for what is plausibly one user-presence concept.

**Fix**: collapse to a single record with optional fields; verify against upstream wire-shape contract.

## 6. Follow/Unfollow mirror pair (LOW)

`FollowRequest` + `FollowResultResponse` ↔ `UnfollowRequest` + `UnfollowResultResponse`. Could share a single request/result base.

**Fix**: small refactor; cohesion benefit.

## 7. The seven stage `*Request` DTOs (MEDIUM)

`DeviceControlRequest`, `KickRequest`, `RaiseHandRequest`, `RaiseHandApprovalRequest`, `StageActionRequest`, `StageInviteRequest`, `StageInviteApprovalRequest` — all share `(cname, userId)` plus 0-3 action-specific fields.

**Fix**: sealed `StageAction` interface + per-action records.

## 8. The four manager `*Request` DTOs (same pattern — see #7)

`ApproveManagerRequest`, `SetManagerRequest`, (plus possibly `ManagerJudgeRequest` if it exists as a request). Same `(cname, userId)` skeleton.

**Fix**: parallel refactor as #7.

## 9. `core/ws/HeartbeatPump` (LOW — single-caller helper)

Used by `HtImUpstreamConnector` (only). Could be inlined as a `@Scheduled`-annotated method once `im`/`realtime` consolidate.

**Fix**: replace with `@Scheduled(fixedDelay=...)` per Phase 3 consolidation.

## 10. `core/ws/ExponentialBackoff` (LOW — single purpose, two callers)

Two callers (`HtImUpstreamConnector`, `RoomEventSource`). Could be replaced by `@Retryable` once consolidation happens.

**Fix**: see Micronaut-adoption report.

## 11. `core/SnakeToCamelJson` (LOW — utility duplication suspect)

The filter `CamelCaseResponseFilter` already does snake_case→camelCase conversion on outbound HTTP responses. Whether `SnakeToCamelJson` is independently used or is redundant needs a 5-line check.

**Fix**: if redundant, delete.

## 12. `core/ws/SequentialSender` (LOW — utility used by connectors)

A serialization helper for WebSocket sends. Single-purpose, two callers. Fine as-is.

**Decision**: leave.

## 13. `core/AuthTokenHolder` and `auth.SessionAuthClientFilter` (FALSE DUPLICATION per audit clarification)

These look similar but solve different problems:
- `AuthTokenHolder`: the BFF's identity to HelloTalk (outbound).
- `SessionAuthClientFilter`: the browser user's identity to the BFF (inbound-tier, gated via cookie).

**Decision**: KEEP BOTH — they are correct complementary mechanisms and the per-session JWT is the inbound user's auth, not the BFF's. Confirmed.

## 14. DTO `CommentListResponse` ↔ `CommentListDto` (MEDIUM)

Structurally identical wrapper around `List<Comment>` vs `List<CommentDto>` — same shape, different inner type. Eliminated for free if #3 is fixed.

## 15. The various `Util` / `Codec` / `Wrapper` classes (LOW)

`EncbinUtil`, `QqTeaCipher`, `TeaCipher`, `Md5Util`, `ApkSignatureGenerator`, `EncryptedFieldCodec`, etc. — each is single-purpose; no duplication; no consolidation needed (though better theming under `com.jilali.platform.crypto.*` would help navigation).

---

## Estimated total duplication impact

| Category | Lines redundantly maintained | Approximate |
|---|---|---|
| `im` ↔ `realtime` parallel packages | `HtImUpstreamConnector` + `HtLiveHubUpstreamConnector` + their event sources + their socket controllers + their mappers | ~500 |
| `JilaliClient` 50+ method God Interface | Implicit duplication across all 7 feature-package controller methods that each spell out upstream-call boilerplate | ~300 (in controllers) |
| `Comment` ↔ `CommentDto` + 2 mappers | 28 fields × 2 + 2 mapper methods | ~150 |
| `RewardItem` ↔ clone | 8 fields × 2 | ~30 |
| Status triplet / follow-unfollow / stage-manager DTO clusters | ~120 |

**Grand total: ~1,100 lines** of duplication-bearable-to-elimination via the Phase 1-3 roadmap.

## Approach to consolidation

The bulk is in two clusters:
1. **Architectural duplication** (`im`/`realtime`, `JilaliClient` God Interface) — fixed by Phase 2 of the rewrite.
2. **DTO duplication** (most of the rest) — fixed by Phase 4-5 consolidation work, which is mostly mechanical "merge two records, delete the other, re-export the merged one."

The audit agents' per-file notes already documented specific consolidation recipes — see the per-file docs for the recipe per duplication.
