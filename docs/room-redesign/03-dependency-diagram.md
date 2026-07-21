# Package Dependency Diagram — New Structure

```mermaid
graph TB
    subgraph API["com.jilali.roomcontext.api"]
        RoomController["RoomController"]
        StageController["StageController (merged into RoomController's\nstage sub-routes, or kept separate —\nsee 07-migration-roadmap.md Phase 2 decision)"]
        RoomWsController["RoomWebSocketController"]
        ApiDto["dto/ (request/response records)"]
        ApiMapper["mapper/ (domain <-> wire DTO)"]
    end

    subgraph Application["com.jilali.roomcontext.application"]
        Commands["command/{room,stage,comment,signin,vip,manager,translation,user}/"]
        Queries["query/"]
        Services["service/ (use-case orchestration,\nStructuredTaskScope fan-out lives here)"]
        PortIn["port.in/ (use-case interfaces\ncontrollers depend on)"]
        PortOut["port.out/ (what application needs\nfrom infrastructure)"]
    end

    subgraph Domain["com.jilali.roomcontext.domain"]
        Model["model/ (Room, Stage, RoomRoster,\nManagerRoster, RoomCommentThread,\nRoomSignIn, VipExperienceCard, UserProfile)"]
        DomainService["service/ (TranslationService)"]
        Event["event/ (RoomEvent sealed hierarchy)"]
        Policy["policy/ (ManagerAuthorizationPolicy, etc.\n- permissive no-op impls)"]
        ValueObject["valueobject/ (Cname, RoomUserId, HostId,\nManagerId, BusiType, MicState, ...)"]
        DomainException["exception/"]
        Repository["repository/ (upstream-facing ports:\nRoomUpstreamPort, CommentUpstreamPort, ...)"]
    end

    subgraph Infrastructure["com.jilali.roomcontext.infrastructure"]
        Client["client/ (declarative @Client interfaces,\none family per capability, replacing\nthe legacy JilaliClient god interface)"]
        Websocket["websocket/ (RoomEventTranslator,\ninbound upstream-push -> RoomEvent,\noutbound RoomEvent -> browser push)"]
        Cache["cache/ (@Cacheable config, if any\nbeyond annotation defaults)"]
        InfraMapper["mapper/ (wire DTO <-> domain model)"]
        Config["configuration/ (@ConfigurationProperties)"]
    end

    RoomController --> PortIn
    StageController --> PortIn
    RoomWsController --> PortIn
    ApiMapper --> ApiDto

    PortIn -.implemented by.-> Services
    Services --> Commands
    Services --> Queries
    Services --> Model
    Services --> PortOut

    PortOut -.implemented by.-> Client
    PortOut -.implemented by.-> Websocket

    Model --> ValueObject
    Model --> Event
    Model --> Policy
    Model --> DomainException
    DomainService --> ValueObject

    Client --> InfraMapper
    InfraMapper --> Model
    Websocket --> Event

    style API fill:#1e3a5f,color:#fff
    style Application fill:#2d4a2d,color:#fff
    style Domain fill:#5f3a1e,color:#fff
    style Infrastructure fill:#3a1e5f,color:#fff
```

## Dependency rules enforced by this structure

1. **`domain` imports nothing from `api`, `application`, or `infrastructure`.** It is the only package with zero outward dependency on the rest of this bounded context — pure Java 25 + JDK only (plus `io.micronaut.serde.annotation.Serdeable` on value objects that cross the wire boundary directly, which is a pragmatic exception discussed in `06-package-dependency-analysis.md`).
2. **`application` imports `domain` (allowed — it orchestrates domain objects) and its own `port.out` interfaces (allowed — but never a concrete `infrastructure` class).** This is what makes the application layer testable without any real HTTP calls: `port.out` interfaces get fake/stub implementations in tests.
3. **`infrastructure` imports `domain` and `application.port.out`** (to implement the ports) but is never imported BY `domain` or `application`. This is the Dependency Inversion Principle made structural: infrastructure depends on the abstraction, not the reverse.
4. **`api` imports `application.port.in`** (the use-case interfaces) — never `domain` directly, and never `infrastructure` at all. Controllers stay thin because they literally cannot reach into domain internals or upstream-call details even if someone tried.

This is the structural fix for the audit's #1-ranked architectural blocker (`docs/audit/reports/dependency-analysis.md`): the legacy `com.jilali.client` package imports feature DTOs AND is imported BY feature packages, a true cycle. In the new structure, **nothing points backward** — `infrastructure.client` depends on `domain.model` (downward), never the other way. There is no package here that both feature code depends on AND that depends back on feature code.
