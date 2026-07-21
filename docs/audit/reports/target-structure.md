# Proposed Target Package Structure

> For a full target architecture, not just the immediate next refactor step. Builds on `architecture.md` §6 and the SOLID / dependency / duplication reports.

## Top-level shape

Following the requested "Hexagonal Architecture (lightweight), Domain/Application/Infrastructure/Api separated, feature-first":

```
com.jilali
├── Application.java
│
├── platform
│   ├── auth-token         ← AuthTokenHolder, JwtUtil, UidExtractor, server-side JWT lifecycle
│   ├── errors             ← JilaliException, ApiError, GlobalErrorHandler, JilaliErrorResponseFilter
│   ├── cryptography       ← all crypto/ files, thematically renamed and documented per-file as wire-interop-only
│   ├── filters            ← DefaultHeadersClientFilter, HeaderPropagationFilter, CamelCaseResponseFilter
│   ├── validation         ← shared validation helpers / constants
│   ├── websocket          ← ExponentialBackoff, HeartbeatPump, SequentialSender, shared UpstreamWebSocketConnector<TEvent> base class
│   ├── config             ← JilaliProperties (renamed to ConfigProperties or kept as-is; depends on whether `@ConfigurationProperties` style is preferred)
│   ├── upstream-dtos      ← wire-shape-only DTOs shared between feature packages and `client/` ports (ConsumerReason DTOs for the personal-IM channel; VoiceRoomInfo for LiveHub; etc.)
│   └── security           ← custom @RequiresOwnedByRoom, @RequiresSelf, etc. (Micronaut @Secured if the security module is added)
│
├── feature
│   ├── chat               ← renamed from `im` + `realtime`. See below for the unification.
│   │   ├── chat.im       ← personal-DM-channel-only code (singleton upstream connector)
│   │   ├── chat.room     ← voice-room-channel-only code (per-cname upstream connector)
│   │   ├── chat.shared   ← UpstreamWebSocketConnector<TEvent> base, frame-decoding strategy interface
│   │   ├── api           ← ChatController (REST), ChatSocketController (WS relay)
│   │   ├── domain        ← ChatMessage, ChatConversation, ChatDelivery (the unwrapped domain types)
│   │   ├── api.dto       ← ChatTransportEvent, ChatOutboundText (the in-package DTOs the controller consumes)
│   │   ├── infra         ← ChatConnection, HtImConnector, HtLiveHubConnector, HtImNotifyMapper (shared infra is in chat.shared or upstream-dtos)
│   │   └── store         ← ChatStore (Angular-side store domain; the BFF proxies it but the Angular store model lives in the frontend)
│   │
│   ├── auth              ← renamed from `auth/`. Now contains only session-cookie-style BFF-internal auth.
│   │   ├── api           ← AuthController
│   │   ├── domain        ← AuthSession, LoginOutcome, SignupOutcome (sealed)
│   │   ├── infra         ← SessionAuthClientFilter, JdbcAuthSessionRepository (encrypted-at-rest column)
│   │   └── service       ← HelloTalkAuthService, HelloTalkAuthClient (interface + impl)
│   │
│   ├── room              ← renamed from `room/`, lifecycle only.
│   │   ├── api           ← RoomLifecycleController, RoomSearchController, CategoryTopicController, RoomAudienceController (split from old monolith `RoomController`)
│   │   ├── api.dto       ← RoomLifecycleRequest/Response, SearchRequest/Result, etc.
│   │   ├── domain        ← Room, Channel, Topic (domain types — not the wire DTOs)
│   │   ├── infra         ← RoomJoinService, RoomsSearchService, TextMatcher
│   │   └── infra.token   ← AgoraTokenCipher
│   │
│   ├── user              ← renamed from `user/`
│   │   ├── api           ← UserController, ProfileController
│   │   ├── api.profile, api.relationships, api.presence, api.batches ← DTOs split along these lines
│   │   ├── domain        ← User profile model (one record, not 30 DTOs)
│   │   ├── infra         ← ProfileBundleService
│   │   └── infra.cache   ← per-cache Caffeine config (user-info, etc.)
│   │
│   ├── translate
│   │   ├── api           ← TranslateController
│   │   ├── domain        ← TranslationRequest, TranslationResponse
│   │   ├── infra         ← TranslateService, TranslateClient (port), HtTranslateClient (adapter)
│   │   └── infra.codec   ← EncryptedFieldCodec
│   │
│   ├── stage             ← thin wrapper over the stage submodule of `feature.room`. Or promoted to a feature if separation proves valuable.
│   ├── manager           ← same
│   ├── vip               ← thin
│   ├── signin            ← thin
│   └── comment           ← thin (could merge into chat if scope is stable)
│
└── infra                 ← alternative organization if hexagonal strictness is preferred
    ├── upstream           ← all upstream-HTTP integration code lives here (was: client/ + portion of im/ + realtime/)
    │   ├── ports          ← TranslateClient, ProfileClient, etc. — feature-facing ports
    │   ├── adapters      ← HtTranslateClient, etc.
    │   └── upstream-dtos  ← wire shapes
    └── application        ← orchestration use-cases (e.g. ProfileBundleService belongs here, not in `user`)
```

Whether to nest `infra/` and `feature/` separately OR to use a flat `platform`, `feature` top-level split is a stylistic choice — both are valid hexagonal layouts. The decision criterion: how much "domain logic" (vs. wire orchestration) is in this codebase. If the answer is "very little" (which appears true — most logic is wire-shape adaptation), then the lighter `platform/feature` split is sufficient. If "moderate," the deeper `domain/application/infra` layering pays off.

## Target structure acceptance criteria

For the package layout to be considered complete:
- [ ] Zero feature package imports from another feature package.
- [ ] Zero feature package imports from any individual `infra`-side DTO defined elsewhere (only `platform.upstream-dtos` shared types).
- [ ] Every `feature/*/infra/` package has NO outgoing imports from `feature/*/api` — direction is correct (api→infra, never inverse).
- [ ] Every `feature/*/api/` package has its own declarative Micronaut HTTP-client sub-interface (replacing the centralized `JilaliClient`).
- [ ] The `platform.upstream-dtos` package contains only types whose lifecycle is "wire shape" — no feature-specific knowledge.
- [ ] The `platform.filters` package contains no feature-specific logic (this is critical for the `core` package's role).
- [ ] The `platform.crypto` package has each cipher's Javadoc clearly state "HelloTalk-wire interop only; not for BFF-internal use."

## Migration map (current → target) at the package level

| Current | Target | Rationale |
|---|---|---|
| `client/` | `platform.upstream.ports` (in pieces), `platform.upstream.adapters` (in pieces) | The God Interface split: each `@Client` interface goes into the feature whose ports-and-adapters it serves. |
| `core/` | `platform.auth-token`, `platform.errors`, `platform.filters`, `platform.websocket`, `platform.config` | Stop the dumping-ground; one package per concern. |
| `crypto/` | `platform.cryptography` | Pure rename + per-file Javadoc clarity. |
| `im/` (8+1 files) | `feature.chat.chat.im` (the personal-DM-only code) + `feature.chat.chat.shared` (the new shared base) + thin transport DTOs in `platform.upstream-dtos` | Unify with `realtime/` under `feature.chat`. |
| `realtime/` | `feature.chat.chat.room` (the room-channel-only code) | Same. |
| `room/` | `feature.room` with split controllers (`api.lifecycle`, `api.search`, `api.browse`, `api.audience`) | Breaks God Controller. |
| `stage/` | merged into `feature.room.api.stage` | Stage operations are room operations. |
| `manager/` | merged into `feature.room.api.manager` | Manager role is a room concept. |
| `comment/` | `feature.chat.api.comment` | Comments are chat-thread items. |
| `signin/` | `feature.signin` (unchanged scope, just renamed) | Standalone promo feature. |
| `vip/` | `feature.vip` (unchanged scope, just renamed) | Standalone promo feature. |
| `user/` | `feature.user` with `api.profile`, `api.relationships`, `api.presence`, `api.batches` | Break the 36-DTO forest. |
| `translate/` | `feature.translate` (renamed + split into api/domain/infra) | Already small; mostly just package boundary. |
| `auth/` | `feature.auth` with `api`, `domain`, `infra`, `service` | Single-tenant BFF: this is the browser-facing auth, not the backend-owns-identity outbound chain. |
| `Application.java` | (unchanged root) | Trivially correct. |

## Sub-package conventions

Every `feature/<name>/api/` package should:
- Hold only `@Controller` methods and request/response DTOs that are BOUND to that controller
- NEVER import from `infra/` of another feature
- Always declare the Micronaut HTTP-client interface for that feature in `infra/client/` and have the controller depend on THAT (not on a generic shared `JilaliClient`)

Every `feature/<name>/domain/` package should:
- Hold plain records / sealed interfaces for the domain model
- Have no dependency on `platform.upstream-dtos` (domain should NOT know about wire shapes)
- Receive mapped instances from `infra/` rather than being instantiated from upstream responses directly

Every `platform.*` package should:
- Be reusable across at least 2 features (else move it INTO the single using feature)
- Document its purpose in a `package-info.java`
- Use only Java stdlib + Micronaut + explicitly-allowed third-party deps

## What this is NOT

- It is NOT a full hexagonal enforcement — only the package structure skeleton.
- It is NOT a one-step migration — see the roadmap for phases.
- It does NOT change the wire protocol or the upstream HelloTalk API.
