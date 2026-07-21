# LoginResponse

`src/main/java/com/jilali/auth/dto/upstream/LoginResponse.java`

## Purpose
The `data` payload of `POST /user_register_center/v3/login`. It models only the `user_info` sub-object (which carries the all-important `jwt` access token); the other returned sections (`pre_register_info`, `countdown_info`, `switch_config`, `banned_info`) are intentionally ignored via the global `FAIL_ON_UNKNOWN_PROPERTIES=false`.

## Responsibilities
- Expose the upstream `user_info`, most importantly `jwt` — the access token every subsequent upstream call authenticates with.

## Public API
- `record LoginResponse(@JsonProperty("user_info") @Nullable UserInfo userInfo)` — single nullable component.
- Nested `record UserInfo(long userId (user_id), @Nullable String jwt, @Nullable String areaCode (area_code), long regTs (reg_ts), int isAdult (is_adult), boolean isNewRegUser (is_new_reg_user), boolean isVip (is_vip))`.

## Dependencies
- `@JsonProperty`, `@Nullable`, `@Serdeable`.
- Depended on BY (grep-confirmed):
  - `HelloTalkAuthClientImpl` (return type of `login`/`performLogin`, deserialized in `readEnvelope`).
  - `HelloTalkAuthClient` (interface return type).
  - `HelloTalkAuthService.login` — maps `LoginResponse::userInfo`, reads `userInfo.userId()`/`userInfo.jwt()` to create the session.
  - `com.jilali.im.HtImUpstreamConnector` — reuses `login` to mint a JWT for the IM socket (`userInfo.jwt()` -> `authTokenHolder.set(...)`).
  - `HelloTalkEnvelope` Javadoc reference.

## Coupling and cohesion analysis
High cohesion — a response value object modeling exactly the fields consumed. Low coupling. It is a genuinely shared upstream type (used by both the `auth` service and the `im` connector), which validates keeping it in a neutral `dto/upstream` package rather than making it auth-private.

## Code smells
- **Data Class:** appropriate for a response DTO.
- **Speculative generality (very mild):** `areaCode`, `regTs`, `isAdult`, `isNewRegUser`, `isVip` are modeled but **not read by any caller** (only `userId` and `jwt` are consumed). See Dead or unused code — these are not dead in the framework sense (Jackson may populate them) but they are presently unused fields carried for completeness/future use.

## Technical debt
- `jwt` is `@Nullable`; callers guard it (`HtImUpstreamConnector` filters `u.jwt() != null && !u.jwt().isBlank()`; the service assumes non-null when creating the session — a null upstream `jwt` on a status-0 response would be persisted as a null session JWT). A defensive check in `HelloTalkAuthService.login` would harden this.

## Duplicate logic
- `UserInfo.userId`/`jwt` overlap conceptually with `AuthSession`'s `helloTalkUid`/`jwt` — but this is the source-to-domain mapping, not duplication.

## Dead or unused code
- No dead types. Unused *fields*: `areaCode`, `regTs`, `isAdult`, `isNewRegUser`, `isVip` have no read site in `src/main/java` (grep-verified — only `userId()`/`jwt()`/`userInfo()` are called). They are populated by Jackson but never consumed; harmless but candidates for removal if the wire completeness isn't needed.

## Refactoring recommendations
- Add a non-null/blank guard on `userInfo.jwt()` in `HelloTalkAuthService.login` (mirroring the connector's guard) before creating a session, so a malformed upstream success can't persist a null-JWT session.
- Optionally trim the unread `UserInfo` fields, or keep them documented as intentionally-modeled-for-future.
