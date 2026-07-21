# `com.jilali.room.dto` — voice-room lifecycle payloads

## Files (23)

Largest single feature DTO set. Organized into 6 conceptual clusters:

### Room/channel shape (largest cluster)
| DTO | Purpose |
|---|---|
| `Channel` | Base room record (cname, busiType, owner, privacy, etc.). |
| `ChannelListItem` | Channel-as-listed (subset of `Channel` for list views). |
| `ChannelListResponse` | Paged wrapper around `ChannelListItem`. |
| `VoiceRoomInfoObjects`, `VoiceRoomInfoResponse` | Voice-room-detail composite payload. |

### Lifecycle command payloads
| DTO | Purpose |
|---|---|
| `CreateVoiceChannelRequest`/`Response` | Create-room bodies. |
| `EndChannelRequest` | End-room. |
| `UpdateVoiceChannelRequest` | Edit-room. |

### Search/browse
| DTO | Purpose |
|---|---|
| `Category`, `CategoryTopicListResponse`, `CategoryTopicTag` | Categories browse tree. |
| `LanguageGroup` | Language-group cluster. |
| `Topic` | Topic shape. |

### User-as-room-member (overlap cluster)
| DTO | Purpose |
|---|---|
| `HostUser` | A room host (likely a permission/styling variant of a plain user). |
| `RoomUser` | A room member. |
| `UserBase` | **Intended** as a shared base for `HostUser`/`RoomUser` — check if it's actually composed or the others just redeclare the fields. |

### Aggregation/sync
| DTO | Purpose |
|---|---|
| `AudienceReconcileResponse` | Roster delta returned on rejoin. |
| `JoinBundleResponse` | Composite "everything for the room page" payload. |
| `RoomLevelConfigResponse` | Server-rendered room-level settings (note: crosses `signin` boundary — its nested `RewardItem` is duplicated 1:1 in `signin/dto.RewardItem`). |
| `BatchChannelStatus`, `BatchQueryRequest`, `BatchQueryResponse` | Bulk channel queries. |

## Dependencies

- Used as request/response types by `RoomController` and the deeply-tangled `client.JilaliClient` declarative methods.
- Re-imported into `user/dto`, `signin/dto` (the `RewardItem` clone), and `manager/dto` for some `Manager`-like shapes.

## ⚠ Top issues (carried forward)

1. `RewardItem` here is an exact 1:1 clone of `signin/dto.RewardItem` — consolidate.
2. `UserBase` vs `HostUser` vs `RoomUser` should be composition (UserBase base, others extend), not redeclaration — check.
3. The 3 search/browse DTOs + 4 lifecycle-DTOs + 6 room-user DTOs collectively form a small forest — group them into sub-packages (`room.dto.lifecycle`, `room.dto.browse`, `room.dto.membership`) in a target rewrite.

## Improvement opportunities

1. **High**: consolidate `RewardItem` across `room.dto` and `signin.dto`.
2. **Medium**: introduce `room.dto.lifecycle`, `room.dto.browse`, `room.dto.membership` sub-packages.
3. **Low**: verify `UserBase` is actually used as a base.
