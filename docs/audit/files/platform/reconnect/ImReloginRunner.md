## Purpose

Singleton bean that performs the IM-channel auto-relogin, triggered when the upstream reports status 105 ("logged in on another device"). Extracted from `HtImUpstreamConnector.attemptRelogin()` in Refactor 10 to (a) apply Micronaut's `@Retryable` to the blocking HTTP relogin call instead of a bare single-attempt `authClient.login(...)`, and (b) collapse three constructor parameters on the connector (`HelloTalkAuthClient authClient`, `String hellotalkEmail`, `String hellotalkPassword`) into one (`ImReloginRunner`).

## Public API

- `@Singleton @Requires(property = "jilali.hellotalk-email", notEquals = "") @Requires(property = "jilali.hellotalk-password", notEquals = "")` — the bean only exists when both credentials are configured.
- `public ImReloginRunner(HelloTalkAuthClient authClient, JilaliProperties properties)`
- `@Retryable(attempts = "3", delay = "500ms", maxDelay = "2s", multiplier = "2.0", includes = {IOException, TimeoutException, HttpClientResponseException}) public Optional<String> attemptRelogin(long userId)` — retries transient upstream failures; returns empty (no retry) on a clean rejection (credentials wrong).

## Dependencies

- **Inbound**: `im/HtImUpstreamConnector` (constructor field, `null` when the bean doesn't exist), `im/ImEventSource` (passes the injected — possibly-null — bean through to the connector).
- **Outbound**: `auth.HelloTalkAuthClient` (`login()`), `core.JilaliProperties` (`hellotalkEmail()`/`hellotalkPassword()`).

## Coupling and cohesion

Single responsibility: mint a fresh JWT for the configured account, with retry. Doesn't touch `AuthTokenHolder` or the WebSocket — the connector still owns publishing the new JWT and reconnecting, keeping this class a pure "get me a fresh token" service.

## Code smells

None.

## Technical debt

None new. Still requires manually checking `reloginRunner == null` at the call site (Micronaut doesn't have a null-object pattern for `@Requires`-gated beans) — this is the standard, idiomatic way to handle an optionally-present bean in Micronaut and is not itself debt.

## Duplicate logic

None — this is the sole implementation of the relogin flow (previously inlined directly in `HtImUpstreamConnector`).

## Dead or unused code

None.

## Java 25 modernization opportunities

None additional — already uses `Optional`, no boilerplate null-checks beyond the DI-optionality check.

## Micronaut built-in opportunities

This class **is** the Micronaut built-in adoption: `@Retryable` replaces what would otherwise be a manual retry loop (the audit's `micronaut-adoption.md` flagged the connectors' hand-rolled backoff as a target; this is the first concrete `@Retryable` usage in the codebase, applied to a scoped, low-risk single-shot HTTP call rather than the full WebSocket reconnect loop, which remains manual per the documented Phase-3 follow-up in `WebSocketConnectionLifecycle.md`).

## Refactoring recommendations

None currently — this is the target shape for "one Micronaut-native concern, one small bean."
