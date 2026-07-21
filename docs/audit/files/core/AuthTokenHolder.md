# AuthTokenHolder

`src/main/java/com/jilali/core/AuthTokenHolder.java`

## Purpose
A `@Singleton` holder of the single live HelloTalk service-account JWT the BFF uses to authenticate upstream calls when no per-user credential is present. It exists so a mid-run relogin (upstream status-105 "logged in on another device") can swap the token in place and have every consumer pick up the fresh value, instead of each consumer capturing a stale copy of `JilaliProperties.defaultAuthToken()` at construction.

## Responsibilities
- Seed the token once at startup from `JilaliProperties.defaultAuthToken()`.
- Expose the current token via `get()` (read live per call).
- Allow in-place refresh via `set()` (called by `HtImUpstreamConnector` on relogin).

## Public API
- `AuthTokenHolder(JilaliProperties properties)` — constructor; seeds an `AtomicReference<String>` from `properties.defaultAuthToken()`.
- `String get()` — current token (never null; `defaultAuthToken()` normalizes null to `""`).
- `void set(String newToken)` — replaces the held token.

## Dependencies
- Injects: `JilaliProperties`.
- Uses: `java.util.concurrent.atomic.AtomicReference`.
- Depended on by: `DefaultHeadersClientFilter`, `ProfileController`, `RoomEventSource`, `TranslateUpstreamHeaders`, `TranslateService`, `ImEventSource`, `ImSendController`, `JilaliGateway`, `HtImUpstreamConnector` (the setter).

## Coupling and cohesion analysis
Very high cohesion — a single-purpose mutable holder. Coupling is broad but intentional and shallow (read-only `get()` for most; only `HtImUpstreamConnector` writes). Thread-safe via `AtomicReference`. This is the correct centralization point for a live-refreshable credential.

## Code smells
- None significant. Borderline **Data Class / global mutable state**, but justified: it is deliberately the single source of truth for a value that must change at runtime.

## Technical debt
- No guard against `set(null)`; a null relogin token would propagate a null through `get()` and break `"Bearer " + authToken.get()` producing `"Bearer null"`. Low risk if callers always pass a valid JWT, but a `set` null-check would harden it.
- Semantics of "single service token" limit multi-account scenarios, but that is out of scope for this app.

## Duplicate logic
None within batch. It is the intended replacement for the previously-scattered `defaultAuthToken()` capture pattern.

## Dead or unused code
None. `get()` and `set()` both used.

## Refactoring recommendations
- Add a null/blank guard in `set()`.
- Consider documenting on the class that `get()` never returns null (invariant relies on `JilaliProperties` normalization).
