# JilaliProperties

`src/main/java/com/jilali/core/JilaliProperties.java`

## Purpose
A `@ConfigurationProperties("jilali")` record binding the `jilali.*` config tree — forwarded-header list, default auth token, crypto keys, device identity, WebSocket origins, and optional relogin credentials — with null-normalizing defaults applied in the compact constructor.

## Responsibilities
- Type-safe binding of config.
- Normalize nullable fields to safe defaults (empty strings, empty/default lists).
- Resolve `deviceId` from config or fall back to `DeviceIdStore.loadOrCreate()`.
- Provide a default `deviceModel` and default allowed WebSocket origins.

## Public API
- `record JilaliProperties(...)` with fields:
  - `forwardedHeaders: List<String>` — normalized to immutable copy or `List.of()`.
  - `defaultAuthToken: String` — `@Nullable` in, normalized to `""`.
  - `agoraCipherKey: String` — `@Nullable` in, normalized to `""`.
  - `serverPubKeyHex: String` — normalized to `""`.
  - `translateServerPubKeyHex: String` — normalized to `""`.
  - `deviceId: String` — config value or `DeviceIdStore.loadOrCreate()`.
  - `deviceModel: String` — config value or `"Samsung Galaxy S21"`.
  - `allowedWebSocketOrigins: List<String>` — config or localhost:4200/4201 defaults.
  - `hellotalkEmail: String` — normalized to `""`.
  - `hellotalkPassword: String` — normalized to `""`.
- All record accessors are public (component accessors).
- Explicit `public String agoraCipherKey()` (line 50) — redundant with the record's generated accessor.

## Dependencies
- Micronaut: `@ConfigurationProperties`, `@Nullable`.
- Calls: `DeviceIdStore.loadOrCreate()`.
- Depended on by: `AuthTokenHolder`, both header filters, `RoomController`, `RoomJoinService`, `ImSocketController`, `ProfileController`, `HelloTalkAuthService/ClientImpl`, `StageController`, `RoomSocketController`, `TranslateService`, `ImEventSource`, `JilaliGateway` (14 files).

## Coupling and cohesion analysis
**Low cohesion / God configuration object**: a single record aggregates unrelated concerns — HTTP header forwarding, service auth token, Agora AES key, two Curve25519 server public keys, device fingerprint, WebSocket CORS origins, and login credentials. It is the single most widely-injected type in the batch (14 dependents), so any config addition touches this one record — a **Shotgun Surgery** magnet. Cohesion suffers because consumers each use a different subset.

## Code smells
- **God Object (config)** / low cohesion: mixes networking, crypto, device identity, CORS, and credentials.
- **Redundant accessor**: `agoraCipherKey()` (line 50) manually re-declares the record's auto-generated accessor — dead/no-op override.
- **Primitive Obsession**: hex keys, tokens, origins all typed as `String`/`List<String>`.
- Compact constructor doing I/O (`DeviceIdStore.loadOrCreate()` reads/writes disk, line 41) is a surprising side effect inside a config record's constructor.

## Technical debt
- Secrets (`defaultAuthToken`, `hellotalkPassword`, crypto keys) live as plain strings in a config record — acceptable for this impersonation tool but note for secret-handling reviews.
- Disk I/O in the constructor makes the record's instantiation non-pure and harder to test.

## Duplicate logic
- The null-to-`""` normalization idiom is repeated ~6 times in the compact constructor (lines 30–35). A small helper would DRY it, though records limit options.

## Dead or unused code
- `public String agoraCipherKey()` (line 50) is redundant — the record already generates an identical accessor. Harmless but removable.
- All bound fields are consumed somewhere across the 14 dependents (grep-confirmed).

## Refactoring recommendations
- Split into cohesive sub-configs: `@ConfigurationProperties("jilali.crypto")` (agora/pubkeys), `jilali.device` (deviceId/model), `jilali.upstream` (forwardedHeaders/token/credentials), `jilali.websocket` (origins). This directly reduces the Shotgun Surgery surface.
- Remove the redundant `agoraCipherKey()` override.
- Move `DeviceIdStore.loadOrCreate()` out of the constructor into a lazily-evaluated bean/factory to keep config binding side-effect-free.
