# SessionAuthClientFilter

`src/main/java/com/jilali/auth/SessionAuthClientFilter.java`

## Purpose
A Micronaut **client** filter (`@ClientFilter(serviceId = "jlhub")`) that bridges *inbound* per-user identity to *outbound* upstream credentials. For each outbound call to HelloTalk (`jlhub`), it reads the inbound browser's `jilali_session` cookie, resolves it to that user's stored HelloTalk JWT via `AuthSessionRepository`, and injects that JWT as the call's `authorization`/`x-ht-token` (plus `x-ht-uid`). This is the mechanism that makes a browser login meaningful beyond `/api/auth/*`: with a valid session, every other controller (rooms, profile, comments) transparently acts as the real logged-in user instead of the shared service account.

## Responsibilities
- On every outbound `jlhub` request, decide the per-user upstream credential.
- Short-circuit if an `authorization` header is already present (a real frontend-supplied header wins).
- Otherwise, look up the inbound session cookie -> `AuthSession` -> inject `authorization`, `x-ht-token`, `x-ht-uid`.
- Order itself (ORDER = 100) to sit between `HeaderPropagationFilter` (real header wins) and `DefaultHeadersClientFilter` (`Integer.MAX_VALUE`, shared fallback token from `AuthTokenHolder`).

## Public API
- `SessionAuthClientFilter(AuthSessionRepository sessions)` — constructor injection.
- `void resolve(MutableHttpRequest<?> downstream)` — `@RequestFilter`; the credential-injection hook.
- `int getOrder()` — returns `100`.

## Dependencies
- Injects: `AuthSessionRepository` (the port, not the JDBC impl — good DIP).
- References `AuthController.SESSION_COOKIE` (the cookie-name constant) and `AuthSession.jwt()`/`helloTalkUid()`.
- Micronaut: `Ordered`, `MutableHttpRequest`, `@ClientFilter`, `@RequestFilter`, `ServerRequestContext`, `Cookie`.
- Depended on BY: nothing calls it directly — it is a framework-invoked `@ClientFilter` bean. Grep for `SessionAuthClientFilter` returns only doc-comment references from `AuthSession`, `AuthController`, and `AuthUserResponse` (Javadoc cross-links), not runtime callers.

## Coupling and cohesion analysis
High cohesion — one job: resolve session -> upstream credential. Coupling is healthy: it depends on the `AuthSessionRepository` abstraction and two well-named constants. The ordering relationship with the two core filters is a form of temporal/positional coupling, but it is explicit, documented, and expressed through `getOrder()` rather than left implicit. This is a clean filter.

## Relationship to `AuthTokenHolder` (important clarification)
This filter and `com.jilali.core.AuthTokenHolder` are **not duplicates and neither is dead code.** They are two rungs of one outbound-credential priority ladder for calls to HelloTalk:
1. Real frontend-supplied `Authorization` header (via `HeaderPropagationFilter`) — highest priority; this filter and the default filter both defer to it.
2. **This filter (order 100):** if a `jilali_session` cookie maps to a live `AuthSession`, use *that user's* JWT — per-user identity, enabling multi-user support for browser clients.
3. `DefaultHeadersClientFilter` (order `MAX_VALUE`) falls back to `AuthTokenHolder.get()` — the single shared service-account JWT the whole backend uses when no per-user session is present.

So `AuthTokenHolder` solves "what identity does the backend use by default upstream" (shared, single mutable token, refreshed on upstream status-105 relogin), while this filter solves "let an individual browser user act as themselves upstream." Different problems, complementary layers.

## Code smells
- **Primitive Obsession (minor):** header names and the `"Bearer "` prefix are inline string literals, duplicated with `DefaultHeadersClientFilter` (`authorization`, `x-ht-token`).
- No smell of note structurally.

## Technical debt
- This is **not an access gate.** The filter never rejects a request; absence of a session silently downgrades the call to the shared service account rather than denying it. There is therefore no server-side authorization boundary here — endpoints outside `/api/auth/*` are reachable by anyone, they simply run as the default account. If per-user access control is ever required, it must be added elsewhere (an inbound server filter), not here.
- `sessions.find(...)` runs a JDBC query on the request path for every outbound `jlhub` call that lacks an `authorization` header, with no caching — a DB round-trip per upstream hop.
- The `"Bearer "` prefix / header names are duplicated across filters (see Duplicate logic).

## Duplicate logic
- The header names `authorization`/`x-ht-token` and the `"Bearer " + token` construction also appear in `com.jilali.core.DefaultHeadersClientFilter.defaultFor(...)`. The two filters build the same header shape from different token sources; a shared helper/constant would remove the literal duplication.

## Dead or unused code
- None. `resolve`/`getOrder` are framework-invoked (`@ClientFilter`/`@RequestFilter`/`Ordered`). Not dead despite having no explicit caller.

## Refactoring recommendations
- Cache session lookups (short TTL, keyed by session id) to avoid a JDBC hit on every outbound hop.
- Extract shared header-name and Bearer-prefix constants (and ideally a single "apply upstream credential" helper) shared with `DefaultHeadersClientFilter`.
- If per-user authorization (not just per-user identity) is a goal, add an inbound server filter that rejects unauthenticated access to protected routes — this client filter cannot serve that purpose.
