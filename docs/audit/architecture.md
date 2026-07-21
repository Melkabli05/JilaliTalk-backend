# jilalibff — Architecture Assessment

> Part of the full backend audit in `docs/audit/`. This document covers the whole-system view; see `docs/audit/packages/*.md` for per-package detail and `docs/audit/files/**/*.md` for per-file detail. Final synthesis reports (technical debt, duplication, dependency analysis, target architecture, roadmap) live in `docs/audit/reports/`.

## 1. What this system is

`jilalibff` is a **Backend-for-Frontend (BFF)** written in Java 25 (preview features enabled) on **Micronaut 4.x**, running on Netty. Its job is to sit between an Angular frontend (`JilaliTalk-angular-frontend`, a separate repo) and **HelloTalk's private mobile API** — a proprietary, unofficial, reverse-engineered upstream that the Android/iOS HelloTalk apps talk to. This backend re-implements HelloTalk's wire protocols (REST + binary WebSocket) in Java, faithfully matching what a real HelloTalk mobile client does, so the Angular frontend never has to speak HelloTalk's binary/encrypted protocols directly.

**Single-account, backend-owns-identity model**: unlike a typical multi-tenant SaaS backend, `jilalibff` is configured with exactly one HelloTalk account's credentials/JWT at the process level. `/ws/im` and `/ws/ht/{cname}` require no per-client authentication from the browser — connecting alone is enough, because the backend's own configured identity is what talks to HelloTalk upstream. This is a deliberate, unusual design decision that shapes almost everything else in the codebase (see §5).

## 2. Technology stack

| Concern | Choice | Notes |
|---|---|---|
| Language | Java 25 | Compiled and run with `--enable-preview` (`build.gradle`) — `RoomJoinService` already uses `StructuredTaskScope` (structured concurrency, still preview in Java 25 GA), proving the codebase already leans on bleeding-edge Java, not just "Java 25 as a version label." |
| Framework | Micronaut 4.5.4 | Compile-time DI (no reflection-based bean scanning at runtime), `micronaut-http-server-netty`, `micronaut-websocket`. |
| Serialization | `micronaut-serde-jackson` + `micronaut-jackson-databind` | Both present — worth checking in the final report whether both are actually needed or if one is legacy. |
| Validation | `micronaut-validation` | Present as a dependency; per-package audits assess how consistently it's actually applied. |
| HTTP clients | `micronaut-http-client`, both declarative (`@Client` interfaces: `JilaliClient`, `ProfileClient`, `VipExperienceCardClient`, `HtTranslateClient`) and imperative (`@Client("jlhub") HttpClient` injected directly into `HelloTalkAuthClientImpl` and `JilaliGateway`) — **inconsistent usage pattern**, see §6. |
| Persistence | H2 (file-based) via `micronaut-jdbc-hikari` | Used for exactly one thing found so far: `auth`'s session table (`AuthSchemaInitializer`/`JdbcAuthSessionRepository`). Not a general-purpose datastore — this is not a typical "backend with a database," it's a stateless proxy with one small local table. |
| Caching | `micronaut-cache-caffeine` | In-memory, used for upstream-response caching (per-package audits identify exact usage, e.g. `user-info` cache in `JilaliGateway`). |
| Crypto | BouncyCastle (`bcprov-jdk18on`) + a hand-rolled `com.jilali.crypto` package | BouncyCastle for standard primitives; the custom package reimplements HelloTalk's own proprietary/legacy ciphers (QQ-TEA, CC2018, HTNT key derivation) purely for wire-protocol interop — this is NOT "should have used a library instead," these ciphers don't exist in any standard library because HelloTalk invented them. |
| Reactive | `reactor-core` | Used for async event streams (IM/room event pub-sub) per `im`/`realtime` packages. |
| Build | Gradle + Shadow plugin | Produces a runnable fat JAR. |

## 3. Package inventory

~200 Java source files across 14 top-level feature packages plus `core`/`crypto`/`client` infrastructure packages:

| Package | Files | Role |
|---|---|---|
| `auth` | 27 | Email login/signup against HelloTalk's own auth API, plus a local JDBC-backed session mechanism for the Angular frontend's own users (see §5.2 — this is NOT the same thing as the backend's single upstream identity). |
| `client` | 5 | Declarative + imperative HTTP client layer to HelloTalk's upstream API. **Architecturally the most concerning package** — see §4. |
| `comment` | 11 | Voice-room comments/captions (live captioning + comment feed). |
| `core` | 17 (14 + `ws` subpkg) | Cross-cutting infra: HTTP filters, error handling, JWT/UID extraction, the `AuthTokenHolder` live-mutable-JWT mechanism, WebSocket helpers (backoff, heartbeat, sequential sender). |
| `crypto` | 8 | Reverse-engineered HelloTalk ciphers (see stack table above). |
| `im` | 9 (8 + `dto`) | Personal 1:1 DM channel: binary WebSocket protocol to HelloTalk's `ht_im/sock`, frame decode, notify mapping, event pub-sub, browser-facing relay. |
| `manager` | 6 | Voice-room "manager" (moderator) role assignment. |
| `realtime` | 7 (5 + `dto`) | Voice-room (LiveHub) channel: structurally parallel to `im` (see §4) — connects to a per-room WebSocket, decodes notify/CC (captioning) frames, relays to the browser. |
| `room` | 28 | Voice-room lifecycle: create/join/end/search/list channels, categories/topics, audience reconciliation. Largest feature package by file count. |
| `signin` | 9 (1 + `dto`) | Daily check-in / reward-claim feature. |
| `stage` | 11 (1 + `dto`) | Voice-room "stage" mechanics: raise-hand, invite-to-stage, kick, mute/device-control. |
| `translate` | 10 (4 + `codec` + `dto`) | AI message translation via a separate encrypted microservice (Curve25519 + AES), SSE streaming. |
| `user` | 39 (3 + `dto`) | HelloTalk user/profile lookups (someone else's profile, not this backend's own auth users). Largest package overall — heavy DTO proliferation, see the `user-dto` package doc for the consolidation table. |
| `vip` | 12 (1 + `dto`) | VIP-experience-card trial/gifting promo feature. |

**11 REST controllers total**, 6 files using `@Client` (2 of them the older imperative-`HttpClient`-injection style rather than declarative interfaces), 6 files using `sealed interface`/`sealed class` (a genuinely modern pattern already present in `auth.LoginOutcome`/`SignupOutcome`, `realtime.dto.RoomCcRealtimeEvent`/`RoomRealtimeEvent`, `im.HtImFrameDecoder`, `im.dto.ImRealtimeEvent`).

## 4. The single biggest architectural finding: `client` is circularly coupled to 7 feature packages

Package-level import analysis (`grep -rn "^import com\.jilali\." src/main/java/com/jilali/<pkg>`) shows:

```
client  -> comment, manager, room, signin, stage, user, vip   (imports feature DTOs)
comment -> client   (calls client interfaces)
manager -> client
room    -> client
signin  -> client, room
stage   -> client
user    -> client, room
vip     -> client
```

This is a **genuine circular dependency**, confirmed by direct inspection: `JilaliClient.java`, `ProfileClient.java`, `VipExperienceCardClient.java`, and `JilaliGateway.java` (all in `com.jilali.client`) import **~65 DTO classes owned by 7 different feature packages** as their `@Client` interface method signatures' parameter/return types (e.g. `JilaliClient` imports from `comment.dto`, `manager.dto`, `room.dto`, `signin.dto`, `stage.dto`, `user.dto`; `JilaliGateway` imports from `room`, `stage.dto`, `user.dto`, `vip.dto`). Meanwhile every one of those feature packages imports `com.jilali.client` right back, to actually invoke the HTTP calls.

**Why this is a problem**: `client` is meant to be the low-level infrastructure/adapter layer (the thing that knows how to talk HTTP to HelloTalk). In any layered or hexagonal design, infrastructure/adapter code should depend on abstractions it owns (ports), and feature/domain code should depend on the adapter through an interface — never the reverse. Here, the wire-transport layer directly references dozens of feature-owned DTOs, meaning:
- `client` cannot be compiled, tested, or reasoned about in isolation from nearly the entire codebase.
- There is no actual "port" boundary — DTOs conflate wire shape with feature/domain meaning, and get passed by reference across what should be a layer boundary.
- Any hexagonal-architecture rewrite (which is explicitly the target for this project) must resolve this first — it's the #1 blocker to a clean layer separation. See the target-package-structure and roadmap reports for the specific resolution strategy (candidates: move client-interface method signatures to use a client-owned wire-DTO set with explicit mapping at the feature-package boundary; or restructure so DTOs live in a lower/shared layer both `client` and features depend on downward).

## 5. Two structurally significant duplications

### 5.1 `im` vs `realtime` — parallel WebSocket-relay architecture, implemented twice

Both packages independently implement the same shape of system: connect to a HelloTalk WebSocket upstream → maintain reconnect/backoff/heartbeat → decode binary/JSON frames → map to typed notify events → fan out via a pub-sub event source → relay to the browser over `jilalibff`'s own WebSocket. Compare:

| Concern | `im` | `realtime` |
|---|---|---|
| Upstream connector | `HtImUpstreamConnector` | `HtLiveHubUpstreamConnector` |
| Notify mapper | `HtImNotifyMapper` | `HtNotifyMapper` + `HtCcNotifyMapper` |
| Pub-sub event source | `ImEventSource` | `RoomEventSource` |
| Browser-facing relay | `ImSocketController` | `RoomSocketController` |
| Event DTO | `im.dto.ImRealtimeEvent` (sealed) | `realtime.dto.RoomRealtimeEvent` + `RoomCcRealtimeEvent` (sealed) |

This is flagged for deep, method-by-method comparison in the dedicated `im`+`realtime` package audit (see `docs/audit/packages/im.md` / `realtime.md` for the full comparison table and a concrete recommendation on whether a shared `UpstreamWebSocketConnector<TEvent>` base is justified, once that audit batch completes).

### 5.2 Two auth-adjacent mechanisms that are NOT duplicates (a clarification, not a smell)

Easy to misread as redundant at first glance — worth stating precisely:
- **`AuthTokenHolder`** (`core`): a single, live-mutable JWT for the ONE HelloTalk account this backend process is configured to act as, used for every OUTBOUND call to HelloTalk's upstream API. Supports auto-relogin on session invalidation.
- **`AuthSession`/`AuthSessionRepository`/`JdbcAuthSessionRepository`/`SessionAuthClientFilter`** (`auth`): a locally-issued session mechanism gating INBOUND requests from browsers talking to this BFF itself.

These solve different halves of the auth story (who is this backend to HelloTalk vs. who is the browser to this backend) and layering them via filter order is a legitimate pattern, not dead code or accidental duplication — this needs confirming against the completed `auth` package audit, but is the working hypothesis based on both mechanisms' actual call sites.

## 6. Micronaut usage: inconsistent, and under-leveraged in places

- **Declarative vs. imperative HTTP clients coexist**: `JilaliClient`/`ProfileClient`/`VipExperienceCardClient`/`HtTranslateClient` use Micronaut's fully declarative `@Client` interface pattern (preferred — auto-marshaling, less boilerplate, testable via mocking the interface). `HelloTalkAuthClientImpl` and `JilaliGateway` instead inject a raw `@Client("jlhub") HttpClient` and build requests imperatively. This split is worth resolving toward one consistent style — likely the declarative one, moving any imperative-only requirement (custom headers per-call, streaming) into a `@Client`-interface-plus-filter combination Micronaut already supports.
- **Both `micronaut-serde-jackson` and `micronaut-jackson-databind`** are declared — the final report should confirm whether both are load-bearing or whether one is legacy weight that can be dropped.
- Per-package audits assess further: whether reconnect/backoff (`im`, `realtime`) reinvents what `@Retryable` offers; whether manual pub-sub reinvents `ApplicationEventPublisher`; whether `@Scheduled` could replace manual heartbeat timers; whether `micronaut-validation` annotations are applied consistently across request DTOs or only in some packages.

## 7. Request flow (typical case)

```
Angular frontend
   │  HTTP (REST) or WebSocket
   ▼
jilalibff Controller (e.g. RoomController, ProfileController)
   │  delegates to
   ▼
Feature service (e.g. RoomJoinService) — optional, some controllers call the client layer directly
   │  calls
   ▼
com.jilali.client (@Client interface or JilaliGateway) ── depends back on feature DTOs (§4)
   │  HTTP, with headers/crypto assembled per-call (device signature, session tokens, AuthTokenHolder JWT)
   ▼
HelloTalk upstream API (private, unofficial)
   │  response envelope (JilaliEnvelope-shaped)
   ▼
JilaliResponses (envelope unwrap) → mapped DTO → Controller → Angular frontend
```

WebSocket flow (`im`/`realtime`) is a persistent variant of the same shape: an upstream connector maintains one long-lived binary WebSocket to HelloTalk, decodes frames continuously, and a separate `@ServerWebSocket` controller relays mapped events to however many browser tabs are currently subscribed (fan-out via a pub-sub event source, not one upstream connection per browser tab).

## 8. Design decisions worth flagging explicitly for the rewrite

1. **Single-account, backend-owns-identity** is a deliberate simplification (no multi-tenant auth complexity against HelloTalk) but conflicts subtly with `auth`'s local session mechanism, which DOES look multi-user-shaped (browser-facing login/signup) — the target architecture needs to decide explicitly whether multi-user support for the Angular frontend's own users is a real requirement or vestigial, since it changes how much of `auth` survives a rewrite.
2. **DTOs as the only contract** — there's no visible domain model layer; DTOs mirror upstream HelloTalk wire shapes directly and are also used as this backend's own API response shapes in many places. A hexagonal rewrite needs an explicit decision on where domain/application-layer types diverge from wire DTOs (or a deliberate decision that they don't, if that's judged not worth the ceremony for a BFF this thin).
3. **Feature-first packages already mostly exist** at the top level (`room`, `stage`, `signin`, `vip`, etc.) — the target "feature-first" reorganization is less of a from-scratch redesign and more a matter of (a) breaking the `client` circular dependency, (b) collapsing the `im`/`realtime` duplication, (c) consolidating the DTO sprawl in `user`/`room`/`stage`, and (d) introducing consistent domain/application/infrastructure sub-layers WITHIN each feature package rather than reshuffling top-level package names.

## 9. Status of this audit

See `docs/audit/packages/*.md` for the full per-package writeups (currently in progress across parallel audit agents) and `docs/audit/files/**/*.md` for per-file detail. Final synthesis reports (`docs/audit/reports/`) consolidate the technical-debt, duplication, dependency, dead-code, Java-25/Micronaut-modernization, and target-architecture findings once every package's file-level audit is complete.
