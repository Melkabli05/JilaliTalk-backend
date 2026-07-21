# JilaliClient.java

`src/main/java/com/jilali/client/JilaliClient.java`

## Purpose
Declarative Micronaut `@Client` HTTP interface for the entire HelloTalk `/livehub` upstream surface. This is the single downstream client for the BFF: one base URL (`jlhub` service id), one auth scheme, ~55 endpoints modeling every feature area (discovery, room lifecycle, stage, manager, comments/captions, sign-in/tasks, user status/profile).

## Responsibilities
- Map every `/livehub/*` upstream endpoint to a typed Java method with `@Get`/`@Post`, `@QueryValue`, `@Body`.
- Faithfully model the two upstream response shapes: bare `{items}` payloads and the `{code,msg,data}` envelope (`JilaliEnvelope<T>`).
- Return the raw envelope (or raw bytes for the binary `/user/profile` endpoint) — it does NOT unwrap; callers use `JilaliResponses.unwrap`.
- Auth/tracing headers are injected externally by `HeaderPropagationFilter`/`DefaultHeadersClientFilter`, not declared here.

## Public API
Interface `JilaliClient`, annotated `@Client(id = "jlhub", path = "/livehub")`. Every method returns `JilaliEnvelope<T>` except `byte[] userProfile(...)`. Representative methods:
- Discovery: `listVoiceRooms`, `listLiveRooms`, `recommendVoiceRooms`, `recommendLiveRooms`, `recommendSingleVoiceRoom`, `languageGroupVoice`, `languageGroupLive`, `categoryTopicList`.
- Info: `voiceRoomInfo`, `liveRoomInfo`, `channelBasicInfo`, `batchQueryChannel`, `liveVoiceConfig`.
- Lifecycle: `createVoiceChannel`, `updateVoiceChannel`, `endChannel`, `userStartedChannel` (`@Nullable` envelope), `userLatestChannel`.
- User room actions: `joinRoom`, `quitRoom`, `heartbeat`, `roomUserList`, `batchUserStatus`, `userEndPageHost`, `userEndPageAudience`, `userRecordLive`.
- Stage: `stageList`, `stageJoin`, `stageQuit`, `raiseHand`, `stageKick`, `raiseHandApproval`, `stageInvite`, `stageInviteApproval`, `deviceControl`, `publisherRtcToken`.
- Manager: `managerList`, `setManagers`, `approveManager`, `managerJudge`.
- Comments/Captions: `comments`, `sendComment`, `captionHistory`, `captionSwitch`.
- Sign-in/Tasks: `voiceSignPanel`, `voiceTasks` (returns `JilaliEnvelope<Object>`), `voiceTaskReward`, `roomLevelReward`, `claimRoomLevelReward`, `roomLevelConfig`.
- Status/profile: `userStatus`, `hostStatus`, `userProfile` (`byte[]`, `APPLICATION_OCTET_STREAM`).
- Nested `@Serdeable record JoinQuitRequest(String cname, @JsonProperty("busi_type") int busiType)`.

## Dependencies
- Imports DTOs from ~7 feature packages: `comment.dto`, `manager.dto`, `room.dto`, `signin.dto`, `stage.dto`, `user.dto`, `core.JilaliEnvelope`.
- Depended on by (grep): `JilaliGateway`, `CommentController`, `SigninController`, `ManagerController`, `RoomController`, `RoomJoinService`, `RoomsSearchService`, `UserController`, `CamelCaseResponseFilter` (import only), plus proxy generation. Invoked via Micronaut-generated proxy, not direct instantiation.

## Coupling and cohesion analysis
Extremely high **efferent coupling**: imports from essentially every feature package's DTO namespace. Cohesion is low by SRP standards — it aggregates unrelated feature verbs (stage control, sign-in rewards, comments, manager admin) into one type. This is a deliberate trade-off documented in the class Javadoc: a BFF talks to one upstream, so a single client avoids duplicating base-URL/auth/timeout config. Acceptable as an infrastructure adapter, but it is a change magnet.

## Code smells
- **Large Interface / God Interface**: ~55 methods spanning 8 feature domains in one type. Not a God *Class* (no logic/state), but a single point every feature must edit — a Shotgun Surgery attractor.
- **Primitive Obsession**: pervasive `int busiType` (magic domain enum passed as raw int on ~15 methods), `long hostId`, `String cname`, `String scene` — no value types. `busiType` especially begs an enum.
- **Inconsistent return typing**: `voiceTasks()` returns `JilaliEnvelope<Object>` and `channelBasicInfo`/`endChannel`/`liveVoiceConfig`/`userEndPage*` return `JilaliEnvelope<Map<String,Object>>` — untyped payloads (see Technical debt).

## Technical debt
- Untyped `Map<String,Object>` / `Object` payloads on ~8 endpoints defer deserialization to ad-hoc casting at call sites (e.g. `SigninController.tasks()` unchecked-casts `voiceTasks()`'s `Object` to `Map` then to `List<Map>`). These should be real DTOs.
- `busiType` magic int has no enum/constant; default values (`2`) are scattered in controllers.

## Duplicate logic
No internal duplication. Cross-file: this is the correct home for all `/livehub` methods. `VipExperienceCardClient` and `ProfileClient` are correctly split out only because they use *different upstream path prefixes* (`/member_privilege_center/...` and `` root) that a single `@Client(path=...)` cannot express per-method — this is a genuine Micronaut constraint, not accidental duplication. No method here overlaps theirs.

## Dead or unused code
None. All methods are reachable via generated proxy from the services listed above; `@Client` interface methods are never "dead" by grep of the interface name. Even endpoints without a direct feature controller (e.g. `hostStatus`, `userStatus`) are called from user/room services outside this batch.

## Java 25 modernization opportunities
- Replace `int busiType` with a sealed/enum `BusiType` — pattern-matchable and self-documenting.
- The nested `JoinQuitRequest` is already a record; fine.
- No imperative logic to modernize here (declarative interface).

## Micronaut built-in opportunities
- `@Retryable` / `@CircuitBreaker` could be added directly to this interface (or per method) instead of any manual retry at call sites.
- The two-shape envelope handling could be centralized: a custom `@Client` `HttpClientResponseExceptionHandler` or a response `@ClientFilter` could unwrap `JilaliEnvelope` and throw `JilaliException` centrally, removing the need for every caller to invoke `JilaliResponses.unwrap` (see JilaliResponses.md).
- `@Nullable` envelope on `userStartedChannel` is already idiomatic.

## Refactoring recommendations
1. Introduce `BusiType` enum to kill the pervasive `int busiType` primitive obsession.
2. Type the `Map<String,Object>`/`Object` endpoints with real records.
3. Consider splitting the interface into feature-scoped `@Client` interfaces sharing the same `jlhub` id + `/livehub` path (Micronaut allows multiple interfaces on one client id) to improve cohesion without duplicating config — mitigates the Shotgun-Surgery/God-Interface smell.
