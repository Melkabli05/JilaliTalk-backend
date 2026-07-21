# Package `com.jilali.auth`

## Purpose
The authentication bounded context of the `jilalibff` BFF. It lets browser clients of the Angular frontend log in and sign up against HelloTalk's private `/user_register_center` mobile API through this backend, and it maintains a server-side, JDBC-backed session so each browser user's requests act upstream as that real HelloTalk identity. It spans the full vertical slice: HTTP controller, application service, two ports (upstream client + session repository) with their implementations, the sealed result types, the domain session record, schema bootstrap, and the client filter that injects per-user credentials on outbound calls.

## Responsibilities
- Expose `/api/auth/**` (login, logout, me, and the four-step signup pipeline) — `AuthController`.
- Orchestrate the auth use-cases and profile enrichment — `HelloTalkAuthService`.
- Speak HelloTalk's two wire codecs (cc2018 / encbin) and crypto for auth flows — `HelloTalkAuthClient` (port) + `HelloTalkAuthClientImpl`.
- Persist verified identities as opaque sessions — `AuthSession`, `AuthSessionRepository` (port), `JdbcAuthSessionRepository`.
- Bootstrap the `auth_session` table on startup — `AuthSchemaInitializer`.
- Model login/signup results without exceptions — `LoginOutcome`, `SignupOutcome`.
- Bridge inbound session cookie -> outbound upstream JWT — `SessionAuthClientFilter`.

## Files in this package
| File | One-line summary |
|------|------------------|
| `AuthController.java` | Thin HTTP boundary for `/api/auth/**`; cookie plumbing + outcome-to-status mapping. |
| `HelloTalkAuthService.java` | Application service orchestrating login/logout/me and the signup pipeline. |
| `HelloTalkAuthClient.java` | Port (interface) to HelloTalk's auth microservice; business-oriented operations. |
| `HelloTalkAuthClientImpl.java` | Concrete client: cc2018/encbin codecs, device personas, crypto, envelope unwrap. |
| `AuthSession.java` | Immutable domain record of a verified identity (id, uid, email, jwt, device, timestamps). |
| `AuthSessionRepository.java` | Port for session persistence (create/find/delete). |
| `JdbcAuthSessionRepository.java` | Raw-JDBC impl over H2; `SecureRandom` session ids, parameterized SQL. |
| `AuthSchemaInitializer.java` | Startup hook that runs `schema.sql` to create `auth_session`. |
| `LoginOutcome.java` | Sealed result: `Authenticated(session,user)` \| `InvalidCredentials()`. |
| `SignupOutcome.java` | Sealed result: `Created(session,user)` \| `Rejected(reason)`. |
| `SessionAuthClientFilter.java` | Client filter resolving the session cookie to a per-user upstream JWT. |

## Dependencies
- **Internal:** `com.jilali.core` (`JilaliException`, `JilaliProperties`, `AuthTokenHolder` indirectly via the filter ordering, `HeaderPropagationFilter`/`DefaultHeadersClientFilter` ordering neighbors); `com.jilali.crypto` (`Md5Util`, `Cc2018Cipher`, `Curve25519SessionGenerator`, `EncbinUtil`, `HtntKeyUtil`, `ApkSignatureGenerator`); `com.jilali.client.JilaliGateway` (profile enrichment — documented inherited coupling pending DDD `user`-context split); `com.jilali.auth.dto` + `com.jilali.auth.dto.upstream`.
- **Framework:** Micronaut HTTP (`@Controller`, `@ClientFilter`, cookies), serde, validation, JDBC `DataSource`, `StartupEvent`.
- **Consumed BY (outside the package):** `com.jilali.im` (`ImEventSource`, `HtImUpstreamConnector`) reuse `HelloTalkAuthClient.login` and `LoginResponse` to mint JWTs for the IM socket — proving these are genuinely shared ports, not auth-private.

## Inbound vs outbound auth (key architectural clarification)
This package's session mechanism and `com.jilali.core.AuthTokenHolder` are **not duplicates.** They form one outbound-credential priority ladder for calls to HelloTalk (`jlhub`):
1. A real frontend-supplied `Authorization` header wins (`HeaderPropagationFilter`).
2. Else `SessionAuthClientFilter` (order 100) resolves the browser's `jilali_session` cookie to that user's stored JWT — **per-user identity**, i.e. multi-user support for frontend users.
3. Else `DefaultHeadersClientFilter` (order MAX) falls back to `AuthTokenHolder.get()` — the **single shared service-account** JWT.

So `AuthTokenHolder` = "the backend's default upstream identity" (one mutable token, refreshed on upstream status-105 relogin); this package's `AuthSession`/repository/filter = "let individual browser users act as themselves upstream." Different problems, complementary layers. Note the filter is *not* an access gate — a missing session silently downgrades to the shared account rather than denying the request.

## Improvement opportunities
- **Highest priority — protect the JWT at rest:** `JdbcAuthSessionRepository` stores the real HelloTalk `jwt` as plaintext in the H2 file (`jwt VARCHAR(4000)`). Anyone with filesystem access obtains live upstream credentials. Encrypt the column (or store an encrypted blob). (Security is safe on two other axes: session ids use `SecureRandom` 256-bit hex, and all SQL is parameterized — no injection.)
- **Session table grows unbounded:** `find` filters expired rows but nothing deletes them. Add `deleteExpired()` + a scheduled reaper.
- **Single source for the 30-day TTL:** duplicated as `AuthController.SESSION_MAX_AGE` and `JdbcAuthSessionRepository.SESSION_TTL`.
- **Cookie hardening:** `secure`/`SameSite` are omitted for localhost; must be config-driven before non-local deployment.
- **`imJwt` exposure:** `AuthUserResponse` ships the real JWT to the browser for its own IM socket — revisit whether the socket can be proxied server-side to restore the "JWT never leaves the backend" invariant.
- **Decompose `HelloTalkAuthClientImpl`:** the one low-cohesion class — extract cc2018/encbin transports and a device-persona value object; give `EmailLoginRequest` a factory to kill its 23-arg positional construction.
- **Richer error taxonomy:** `InvalidCredentials`, `Rejected(String)`, and `Optional.empty()` all discard upstream error detail the frontend might want.
