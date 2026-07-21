# ProfileClient.java

`src/main/java/com/jilali/client/ProfileClient.java`

## Purpose
Separate declarative `@Client` for profile/relation endpoints that live at the HelloTalk API root (`https://api-global.hellotalk8.com/`) rather than under `/livehub`. Shares the `jlhub` service id (same base URL config, timeouts, filters) but with an empty `path` prefix so it can hit root-level paths that `JilaliClient`'s `/livehub` prefix would otherwise force.

## Responsibilities
- Map ~18 profile/relation endpoints (me, followers, followings, follow, unfollow, visits, like count, langs, stats, visitors, edit profile, limitations, increment, pay-chat info, reminder moment, block list, user tags).
- Model the *different* upstream envelope: `{"status":0,"message":"success","data":...}` — distinct from `JilaliClient`'s `{code,msg,data}`.
- Declare nested request-body records with explicit `@JsonProperty` snake_case names.

## Public API
Interface `ProfileClient`, `@Client(id = "jlhub", path = "")`. Methods return feature-specific response records (`ProfileMeResponse`, `FollowersResponse`, `FollowingResponse`, `FollowResultResponse`, `UnfollowResultResponse`, `HttpResponse<Void>` for `recordVisit`, `LikeCountResponse`, `UserLangsResponse`, `ProfileStatsResponse`, `VisitorsResponse`, `ProfileEditResponse`, `ProfileLimitationsResponse`, `ProfileIncrementResponse`, `PayChatInfoResponse`, `ReminderMomentResponse`, `BlockListResponse`, `UserTagsResponse`). Note: these are NOT wrapped in `JilaliEnvelope`.
- Nested `@Serdeable` records: `FollowBody`, `UnfollowBody`, `VisitBody`, `ProfileMeBody`, `IncrementBody` — all with explicit `@JsonProperty` names.

## Dependencies
- Imports ~18 `user.dto` types.
- Depended on by (grep): `ProfileBundleService`, `ProfileController`, `VisitorHistoryRequest` (import). Called via generated proxy.

## Coupling and cohesion analysis
Reasonably cohesive — all endpoints are profile/relation domain and share the root path + `{status,message,data}` envelope. Coupling to `user.dto` is expected. Correctly separated from `JilaliClient` on a real technical axis (path prefix + envelope shape), not accidental fragmentation.

## Code smells
- **Primitive Obsession**: many `int`/`long`/`String` query params (`terminalType`, `focusTab`, `osType`, `terminal_type`, `client_os`) as raw primitives with no domain types.
- **Data Clumps**: client-metadata params (`client_os_lang`, `version`, `os_type`) recur across `increment`, `userTags`, etc.
- `stats(@Body Map<String, Object> body)` — untyped body (Primitive Obsession / missing DTO).

## Technical debt
- `stats` takes an untyped `Map<String,Object>` body instead of a record.
- Response types are NOT enveloped here, so callers unwrap `.data()` ad hoc (`ProfileController`, `ProfileBundleService`) — a parallel, non-`JilaliResponses` unwrap path for the `{status,message,data}` shape. There is no `ProfileResponses` equivalent utility, so each caller reimplements null-safe `.data()` extraction.

## Duplicate logic
- The nested body records replicate the same explicit-`@JsonProperty` pattern documented across the codebase (`VisitBody` doc references `FollowersResponse.FollowerUser`). Not true duplication, but a recurring manual workaround for the absence of a global snake_case naming strategy.
- Overlap with `JilaliClient`: none — disjoint endpoints. Correct split.

## Dead or unused code
None obvious; used by `ProfileBundleService`/`ProfileController` (outside this batch). `@Client` proxy methods are not dead by grep.

## Java 25 modernization opportunities
- Replace `stats`'s `Map<String,Object>` body with a record.
- Bundle recurring client-metadata params (`client_os_lang`, `version`, `os_type`) into a shared record passed as `@RequestBean` (Micronaut) — reduces the data-clump.

## Micronaut built-in opportunities
- A global `@JsonNaming`/serde snake_case naming strategy (Micronaut Serde `PropertyNamingStrategy`) would eliminate the per-field `@JsonProperty` annotations on every body record (the codebase deliberately doesn't configure one — see doc comments).
- Response unwrapping for the `{status,message,data}` shape could be centralized via a `@ClientFilter` mirroring the `JilaliResponses` recommendation, giving a uniform unwrap path.
- `@RequestBean` for the client-metadata data clump.

## Refactoring recommendations
1. Introduce a `ProfileResponses.unwrap` (or a shared filter) mirroring `JilaliResponses` so `.data()` extraction isn't reimplemented in each caller.
2. Type the `stats` body.
3. Consider a global serde naming strategy to remove the `@JsonProperty` boilerplate across all body records.
