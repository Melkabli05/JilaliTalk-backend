# AuthController

`src/main/java/com/jilali/auth/AuthController.java`

## Purpose
The HTTP boundary for the `auth` feature. It exposes the `/api/auth/**` REST endpoints the Angular frontend calls for login, logout, "who am I", and the multi-step email signup flow. It is deliberately thin: it does cookie plumbing and maps already-decided `LoginOutcome`/`SignupOutcome` values to HTTP status codes, delegating every business decision to `HelloTalkAuthService`.

## Responsibilities
- Declare the `/api/auth` REST surface (`@Controller`, `@ExecuteOn(BLOCKING)`).
- Translate request DTOs into service calls and service outcomes into `HttpResponse`s.
- Set/clear the opaque `jilali_session` HttpOnly cookie on login/signup/logout.
- Read the session id back out of the inbound cookie for `/logout` and `/me`.
- Map sealed-outcome variants to status codes (200/201/401/422) via exhaustive `switch`.

## Public API
- `public static final String SESSION_COOKIE = "jilali_session"` — the cookie name; also read by `SessionAuthClientFilter`.
- `AuthController(HelloTalkAuthService authService)` — constructor injection.
- `HttpResponse<?> login(LoginRequest)` — `POST /api/auth/login`; 200 + user + cookie, or 401.
- `HttpResponse<Void> logout(HttpRequest)` — `POST /api/auth/logout`; deletes session, clears cookie, 204.
- `HttpResponse<AuthResponse> me(HttpRequest)` — `GET /api/auth/me`; 200 + user or 401.
- `HttpResponse<Void> signupPrepare()` — `POST /api/auth/signup/prepare`; best-effort anti-cheat bind, 204.
- `HttpResponse<Void> sendEmailCode(SendEmailCodeRequest)` — `POST /api/auth/signup/send-email-code`; 204.
- `HttpResponse<Void> checkNickname(NicknameCheckRequest)` — `POST /api/auth/signup/check-nickname`; 204.
- `HttpResponse<?> completeSignup(SignupCheckRequest)` — `POST /api/auth/signup/check`; 201 + user + cookie, or 422 + reason.
- Private `sessionId(HttpRequest)` and `withSessionCookie(...)` helpers.

## Dependencies
- Injects: `HelloTalkAuthService` (the only collaborator).
- Uses DTOs: `AuthResponse`, `LoginRequest`, `NicknameCheckRequest`, `SendEmailCodeRequest`, `SignupCheckRequest`, plus `LoginOutcome`/`SignupOutcome` sealed types.
- Micronaut HTTP annotations, `Cookie`, `@Valid`.
- Depended on BY: nothing in `src/main/java` calls it directly (framework-invoked endpoints). `SessionAuthClientFilter` references its `SESSION_COOKIE` constant.

## Coupling and cohesion analysis
High cohesion — every method is an HTTP adapter concern, and business logic is correctly pushed into the service. Coupling is healthy: it depends only on the concrete `HelloTalkAuthService` (a `final` class, not an interface, but that is a single application service so an abstraction would be over-engineering). The `switch` over sealed `LoginOutcome`/`SignupOutcome` gives compile-time exhaustiveness. This class is a good example of a thin controller.

## Code smells
- None significant. The 422 branch returns a raw `String` reason as the body (`completeSignup`), which is a minor inconsistency with the `{user: ...}` envelope shape used elsewhere — a tiny Primitive Obsession / inconsistent-contract nit, not a structural smell.

## Technical debt
- Cookie deliberately omits `secure(true)` and `SameSite` (documented in the `withSessionCookie` Javadoc) — a hardcoded dev-only compromise that MUST change before non-localhost deployment.
- `SESSION_MAX_AGE` (30 days) is duplicated as `SESSION_TTL` in `JdbcAuthSessionRepository` — the cookie lifetime and the DB-row TTL are the same number defined in two places (see Duplicate logic).
- No tests (consistent with the project's "no unit tests unless asked" stance).

## Duplicate logic
- The 30-day session lifetime constant exists both here (`SESSION_MAX_AGE`) and in `JdbcAuthSessionRepository` (`SESSION_TTL`). They must stay in sync but nothing enforces it.

## Dead or unused code
- No dead code. All endpoint methods are framework-invoked (Micronaut routes them by annotation); absence of explicit callers in `src/main/java` is expected and not a defect.

## Refactoring recommendations
- Extract the 30-day duration into a single shared constant (or config property) referenced by both the controller cookie and the repository TTL.
- Consider returning a structured error body (e.g. `{error: reason}`) from the 422 branch to match the frontend's JSON expectations rather than a bare string.
- When moving beyond localhost, drive `secure`/`SameSite` from a config flag rather than hardcoding.
