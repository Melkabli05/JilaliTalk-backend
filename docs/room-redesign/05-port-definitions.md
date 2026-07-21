# Port Definitions

Hexagonal architecture has two families of ports. This document defines the shape of each; concrete method lists are derived mechanically from `04-use-case-map.md` (one `port.in` method per Command/Query) plus `01-domain-model.md` (one `port.out` method per upstream call the domain/application layer needs).

## `port.in` — inbound ports (use-case interfaces)

One interface per aggregate/capability, implemented by an `application.service` class, called by `api` controllers. Naming convention: `<Capability>UseCases`.

```java
// application/port/in/RoomUseCases.java
public interface RoomUseCases {
    RoomListResponse listVoiceRooms(ListVoiceRoomsQuery query);
    RoomListResponse listLiveRooms(ListLiveRoomsQuery query);
    RoomInfo getVoiceRoomInfo(Cname cname);
    RoomInfo getLiveRoomInfo(Cname cname);
    JoinRoomResult joinRoom(JoinRoomCommand command);       // fan-out use case
    void endRoom(EndRoomCommand command);
    RoomInfo createVoiceRoom(CreateVoiceRoomCommand command);
    // ... one method per room-area row in 04-use-case-map.md
}

// application/port/in/StageUseCases.java
public interface StageUseCases {
    StageOccupantsView listStageOccupants(Cname cname);
    void joinStage(JoinStageCommand command);
    void quitStage(QuitStageCommand command);
    void raiseHand(RaiseHandCommand command);
    void approveRaiseHand(ApproveRaiseHandCommand command);
    void kick(KickFromStageCommand command);
    void invite(InviteToStageCommand command);
    void approveInvite(ApproveStageInviteCommand command);
    void controlDevice(ControlStageDeviceCommand command);
    PublisherToken getPublisherToken(Cname cname, RoomUserId userId);
}

// application/port/in/ManagerUseCases.java
// application/port/in/CommentUseCases.java
// application/port/in/SignInUseCases.java
// application/port/in/VipUseCases.java
// application/port/in/UserProfileUseCases.java
// application/port/in/TranslationUseCases.java
```

**Why one interface per capability, not one giant `RoomContextUseCases`**: this is the direct analogue of the god-interface lesson learned from `client.JilaliClient` — a single sprawling interface would recreate the exact problem this redesign exists to fix, just one layer up. Each `api` controller depends on only the `port.in` interfaces it actually calls (`RoomController` depends on `RoomUseCases` + `StageUseCases`; it does NOT depend on `VipUseCases`).

## `port.out` — outbound ports (what the application/domain layer needs from the outside world)

These are the **upstream-facing repository ports** referenced in `01-domain-model.md`. Naming convention: `<Capability>UpstreamPort`.

```java
// application/port/out/RoomUpstreamPort.java
public interface RoomUpstreamPort {
    ChannelList listVoiceRooms(int langId, int limit, int offset, int refresh);
    ChannelList listLiveRooms(...);
    RoomInfo fetchVoiceRoomInfo(Cname cname);
    RoomInfo fetchLiveRoomInfo(Cname cname);
    RoomInfo createVoiceRoom(CreateVoiceRoomRequest request);
    void endRoom(Cname cname);
    // ...
}

// application/port/out/StageUpstreamPort.java
public interface StageUpstreamPort {
    StageOccupants fetchStageList(Cname cname, BusiType busiType);
    void requestJoinStage(Cname cname, RoomUserId userId, ...);
    void requestKick(Cname cname, RoomUserId target, ...);
    // ...
    PublisherToken fetchPublisherToken(Cname cname, RoomUserId userId);
}

// application/port/out/CommentUpstreamPort.java
// application/port/out/SignInUpstreamPort.java
// application/port/out/VipUpstreamPort.java
// application/port/out/UserProfileUpstreamPort.java
// application/port/out/TranslationUpstreamPort.java   // <- already exists! see below

// application/port/out/RoomEventPublisherPort.java
public interface RoomEventPublisherPort {
    void publish(RoomEvent event);   // implemented via ApplicationEventPublisher<RoomEvent>
}
```

**`TranslationUpstreamPort` already exists in spirit**: the legacy `translate.TranslateClient` interface is exactly this shape, and the audit confirms its port-and-adapter design is correct (`docs/audit/packages/translate.md`). The new structure simply relocates it to `application.port.out.TranslationUpstreamPort` (interface unchanged) with `infrastructure.client.HtTranslateClient` (unchanged from legacy `HtTranslateClient`) as its sole implementation. **This is the one piece of the redesign that is a pure move, not a rewrite** — worth calling out explicitly since the rest of this document describes new interfaces.

## Implementations

| Port | Implemented by | Notes |
|---|---|---|
| `RoomUpstreamPort` | `infrastructure.client.RoomJilaliClient` (declarative `@Client(id="jlhub", path="/livehub")`) | Replaces the room-related methods currently on the legacy god interface `client.JilaliClient`. |
| `StageUpstreamPort` | `infrastructure.client.StageJilaliClient` | Same `id`/`path`, separate interface — see `06-package-dependency-analysis.md` for why this doesn't duplicate configuration. |
| `CommentUpstreamPort` | `infrastructure.client.CommentJilaliClient` | |
| `SignInUpstreamPort` | `infrastructure.client.SignInJilaliClient` | |
| `VipUpstreamPort` | `infrastructure.client.VipJilaliClient` | |
| `UserProfileUpstreamPort` | `infrastructure.client.UserProfileJilaliClient` + `infrastructure.client.ProfileJilaliClient` (the legacy `ProfileClient` — already its own small interface, kept, just relocated) | |
| `TranslationUpstreamPort` | `infrastructure.client.HtTranslateClient` (unchanged, moved) | |
| `RoomEventPublisherPort` | `infrastructure.websocket.MicronautEventPublisherAdapter` | Thin wrapper over Micronaut's `ApplicationEventPublisher<RoomEvent>` — exists mainly so `application`/`domain` code never imports `io.micronaut.*` directly, keeping the domain layer framework-agnostic. |

## Test doubles

Because every `port.out` is an interface, `application.service` classes are unit-testable with hand-written or mock implementations that never touch HTTP — this is the concrete payoff of "Easy testing" from the goal's Architecture Principles list. No `@MicronautTest`/Netty test server needed for use-case-level tests; only the thin `infrastructure.client` adapters need integration-style tests against a recorded/stubbed upstream response.
