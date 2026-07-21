# RoomEventSource

`src/main/java/com/jilali/realtime/RoomEventSource.java` — `@Singleton public class`.

## Purpose
Per-room pub-sub bridge. First browser subscriber for a `cname` opens the LiveHub upstream; last to leave closes it. Fans room events into a per-room Reactor multicast sink, with a parallel CC (subtitle) sink, roster-revision tracking, and late-join connection-state replay.

## Public API
- `RoomEventSource(HtNotifyMapper, HtCcNotifyMapper, AuthTokenHolder, ObjectMapper)`.
- `Flux<RoomRealtimeEvent> subscribe(String cname)` — ref-counts; first opens upstream, replays `lastConnectionState` to late joiners.
- `Flux<RoomCcRealtimeEvent> subscribeCc(String cname)` / `void unsubscribeCc(String)` — opt-in subtitle stream; does not itself open the upstream.
- `void unsubscribe(String cname)` — last subscriber closes upstream and completes sinks.
- `int audienceRevision(String cname)` — roster revision (bumps on join/quit/stage-join/kick).

## Coupling
Injects both mappers, `AuthTokenHolder`, `ObjectMapper`; constructs `HtLiveHubUpstreamConnector` per room. Consumed by `RoomSocketController`.

## Notes
Structural parallel to `com.jilali.im.ImEventSource` (same "first opens / last closes / multicast + replay-state" pattern; Room is the per-`cname` multi-tenant generalization, adds CC channel, no outbound send).

**`connectorUserId` derivation (constructor/subscribe):** verified — line 79 reads `UidExtractor.uidAsString(authToken.get(), om)` from the injected `AuthTokenHolder`, i.e. the live token. Refactored away from `JilaliProperties.defaultAuthToken()`.
