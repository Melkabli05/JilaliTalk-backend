# DefaultHeadersClientFilter

`src/main/java/com/jilali/core/DefaultHeadersClientFilter.java`

## Purpose
A `@ClientFilter(serviceId = "jlhub")` that supplies default device-context, auth, User-Agent, tracing, and derived `x-ht-uid` headers on every outbound upstream request, filling gaps the frontend does not send. Runs last (`getOrder() == Integer.MAX_VALUE`) so real headers from the frontend / session filter win.

## Responsibilities
- Overwrite `User-Agent` with the smali-canonical `android;<ver>;<model>;<osVer>;<uid>` string.
- For each configured forwarded header absent on the request, supply a hardcoded default (`defaultFor`).
- Derive `x-ht-uid` from the inbound JWT (`deriveCallerUid`) since the frontend never sends it.
- Always generate fresh B3 tracing headers (`x-b3-*`, `x-request-start`).

## Public API
- `DefaultHeadersClientFilter(JilaliProperties properties, AuthTokenHolder authToken, ObjectMapper om)` — constructor.
- `@RequestFilter void addDefaults(MutableHttpRequest<?> downstream)` — framework-invoked.
- `int getOrder()` — returns `Integer.MAX_VALUE` (run after `HeaderPropagationFilter` and `SessionAuthClientFilter`).

## Dependencies
- Injects: `JilaliProperties`, `AuthTokenHolder`, `ObjectMapper`.
- Uses: `JwtUtil.uidFromBearer`, `UidExtractor.uidAsLong`, `ApkSignatureGenerator.VERSION_NAME`, `ServerRequestContext`.
- Depended on by: framework-instantiated; referenced in Javadoc/comments by `SessionAuthClientFilter`, `HelloTalkAuthClientImpl`, `TranslateUpstreamHeaders`, `JilaliGateway`.

## Coupling and cohesion analysis
Moderate cohesion but the class does several loosely related things (auth token defaulting, device fingerprint defaults, UA construction, uid derivation, tracing). Coupling is the highest of the filter set: it reaches into `JwtUtil`, `UidExtractor`, `ApkSignatureGenerator`, `AuthTokenHolder`, and `JilaliProperties`. It correctly reads `AuthTokenHolder.get()` live (lines 97, 133, 167) rather than capturing the token.

## Code smells
- **Long Method**: `addDefaults` (lines 47–93) plus a large `defaultFor` `switch` (95–119) mixing auth, locale, device, and channel concerns.
- **Primitive Obsession / hardcoded magic strings**: device-context defaults are inline literals (`"6.1.0"`, `"ios"`, `"18.5"`, a hardcoded `x-ht-did` hash at line 112). The `"18.5"` OS version is duplicated at line 110 and line 163 with a `TODO`.
- **Feature Envy**: `buildUserAgent` and `deriveCallerUid` assemble values almost entirely from other classes' data.
- Inconsistency: `defaultFor` case `"user-agent"` (line 101) is dead relative to the unconditional overwrite at line 61 — retained only for the forwarded-headers config path; documented but confusing.

## Technical debt
- Hardcoded `x-ht-did` device id (line 112) sits beside a real persisted `DeviceIdStore` — the persisted device id is not wired into this default.
- `x-ht-os = "ios"` (line 103) contradicts the `android;...` User-Agent (line 160): a persona inconsistency that may matter to upstream fingerprinting.
- `"18.5"` OS version hardcoded twice with an unresolved `TODO` (line 163).

## Duplicate logic
- Two different JWT→uid extractors are used in the same class: `JwtUtil.uidFromBearer` (line 134) and `UidExtractor.uidAsLong` (line 167). See the JwtUtil / UidExtractor docs — these two utilities duplicate JWT-payload decoding and should be unified.
- OS-version string `"18.5"` duplicated (lines 110, 163).

## Dead or unused code
- `defaultFor` `"user-agent"` branch is effectively superseded by the unconditional `buildUserAgent()` overwrite; only reachable via the forwarded-headers config list. Not truly dead but near-dead; flagged in-code.
- `addDefaults` is framework-invoked (`@RequestFilter`) — not dead.

## Refactoring recommendations
- Extract the device-context defaults into `JilaliProperties` (or a dedicated `DevicePersona` config record) so they stop being scattered literals.
- Unify uid extraction on a single utility (see cross-file duplicate finding).
- Reconcile `x-ht-os`/User-Agent persona and wire `DeviceIdStore` into `x-ht-did`.
- Split `addDefaults` into `applyAuthDefaults`, `applyDeviceDefaults`, `applyTracing`.
