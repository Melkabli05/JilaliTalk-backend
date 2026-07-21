# Domain Model — Room Bounded Context

## Design principle: one bounded context, multiple aggregates

The goal document asks that "the Room aggregate should own the majority of business rules" and that we "avoid anemic domain models whenever practical." Taken literally, one might try to force every capability (comments, sign-in, VIP, user profile, translation) to be a child entity of a single `Room` aggregate. That would be wrong, and here's the concrete reason: **aggregate boundaries are drawn around transactional consistency requirements, not around "things that relate to the same feature."**

- A room's **Stage** occupancy (who's speaking, mic state, raise-hand queue) and its **roster/manager list** genuinely need same-instant consistency with the room itself — you cannot allow a stage-seat assignment to commit while the room's own occupancy count is stale. These belong inside the `Room` aggregate.
- A room's **comment feed** does not need that same-instant consistency — a comment can be appended a few hundred milliseconds after the room's occupancy changes with zero business-rule violation. Comments are also unbounded and append-only, the classic signal for a separate aggregate.
- **Sign-in rewards**, **VIP cards**, and **user profile data** have lifecycles that outlive any single room visit (a VIP card claimed today is used in a different room next week; a user's profile doesn't stop existing when they leave a room). Forcing these inside `Room` would make `Room` responsible for consistency it has no authority over — HelloTalk's own backend is the actual system of record for all of these, `Room` has no more claim over "did this user's sign-in reward get claimed" than it does over "is this user's profile up to date."

So: **one bounded context, five aggregate roots**, all living under `com.jilali.roomcontext.domain.model`, all belonging conceptually to "capabilities of interacting with a room," none of them arbitrarily nested inside another where the nesting wouldn't reflect a real consistency requirement.

## Aggregate roots

### 1. `Room` (primary aggregate)

The room's own lifecycle, occupancy, stage, and manager roster — the one aggregate with genuine same-transaction invariants.

```java
public final class Room {
    private final Cname cname;
    private final HostId hostId;
    private final BusiType busiType;          // voice vs live — see Value Objects
    private RoomLifecycleState lifecycleState; // CREATED, LIVE, ENDED
    private RoomLevel level;                   // sealed: config + reward tier, see Value Objects
    private final Stage stage;                  // child entity — see below
    private final RoomRoster roster;            // child entity — see below
    private final ManagerRoster managers;       // child entity — see below

    // Behavior (business rules live here, not in a service):
    public StageAssignment assignStageSeat(RoomUserId userId, SeatRequest request) { ... }
    public void vacateStageSeat(RoomUserId userId) { ... }
    public RaiseHandTicket queueRaiseHand(RoomUserId userId) { ... }
    public void approveRaiseHand(RoomUserId userId, ManagerId approver) { ... }
    public void kick(RoomUserId target, ManagerId actor) { ... }
    public void grantManager(RoomUserId target, HostId grantedBy) { ... }
    public void revokeManager(RoomUserId target, HostId revokedBy) { ... }
    public void end(HostId endedBy) { ... }
}
```

**Child entities (inside the `Room` consistency boundary):**

- `Stage` — current occupants (0..N seats, N from `RoomLevel`/config), the raise-hand queue (FIFO), pending invites.
- `RoomRoster` — every current participant (`RoomMember`: userId, role, mic/cam state, ban state). This directly replaces the legacy `RoomUser` record's 20 fields — see Value Objects below for how those 20 fields decompose.
- `ManagerRoster` — the set of currently-granted managers for this room.

**Why `Stage` and `ManagerRoster` are child entities, not separate aggregates**: a stage-seat assignment must check "is this user currently a room member" and "is there a free seat" atomically with the room's own state — splitting them into separate aggregates would reintroduce the exact cross-aggregate consistency problem DDD aggregate design exists to prevent. This directly resolves the audit's "God Class" complaint about `RoomController` (`docs/audit/packages/room.md` improvement #1) — the complexity was never really about the *controller*, it was that the controller had nowhere else to put "is this a valid stage transition" logic. Once that logic lives in `Room`/`Stage`, the controller (now `api/RoomController`) shrinks to routing + DTO mapping only.

### 2. `RoomCommentThread` (aggregate)

```java
public final class RoomCommentThread {
    private final Cname cname;
    private final List<Comment> comments;   // append-only

    public Comment post(RoomUserId author, CommentText text, Optional<ReplyTarget> replyTo) { ... }
}
```

Independent lifecycle from `Room` (referenced by `Cname` only, not held as a `Room` field) — a room can be joined/left without touching its comment thread, and vice versa. This directly resolves the legacy `Comment`/`CommentDto` 28-field duplication (already fixed in the legacy code during the prior refactor pass, Refactor 5) by making `Comment` the one domain-level shape, with the millisecond/second wire-unit conversion pushed entirely into the infrastructure mapper (see `08-class-mapping.md`).

### 3. `RoomSignIn` (aggregate)

```java
public final class RoomSignIn {
    private final RoomUserId userId;
    private final Cname cname;              // which room's daily-task campaign this is
    private final List<SignInDay> calendar;
    private final List<PendingReward> claimable;

    public ClaimedReward claim(RewardId rewardId) { ... }
    public ClaimedReward claimTask(TaskId taskId) { ... }
}
```

Modeled as its own aggregate (not nested in `Room`) because a sign-in claim's correctness depends only on the user's own calendar state, never on the room's live occupancy. This also directly resolves the audit's cross-package `RewardItem` duplicate finding (`signin.dto.RewardItem` vs `room.dto.RoomLevelConfigResponse.RewardItem`, already fixed in the legacy code as `platform.models.RewardItem` during Refactor 3) — the new domain reuses one `RewardItem` value object, no re-declaration.

### 4. `VipExperienceCard` (aggregate)

```java
public final class VipExperienceCard {
    private final CardId id;
    private final RoomUserId owner;
    private CardState state;      // UNCLAIMED, CLAIMED, USED
    private final List<CardFeature> features;

    public void claim() { ... }              // UNCLAIMED -> CLAIMED
    public UsedFeature use(FeatureId featureId) { ... }  // CLAIMED -> USED (per-feature)
    public void receiveFromFriend(RoomUserId sender) { ... }
}
```

The audit's own `vip.md` doc explicitly validates the legacy `Card → Detail → Feature → UsedFeature` shape as "justified hierarchical decomposition, not duplication" — this design keeps that shape, just expressed as one aggregate with a real state machine (`UNCLAIMED → CLAIMED → USED`) instead of four independent DTOs with no state-transition enforcement. **This is a genuine behavioral tightening**: the legacy code has no domain-level guard against using a card twice or using an unclaimed card — `VipExperienceCard.use()` can now enforce `state == CLAIMED` before transitioning, catching a misuse class the legacy DTO-only model couldn't. (Note: this is a business-rule improvement, not a security/authorization one — it's about state-machine correctness, not about verifying *who* is allowed to call `use()`, which remains explicitly out of scope per the durable no-auth-work instruction.)

### 5. `UserProfile` (aggregate, read-model-heavy)

```java
public final class UserProfile {
    private final RoomUserId userId;
    private ProfileSummary summary;      // nickname, avatar, nationality, language levels
    private PresenceStatus presence;     // online/offline/in-room
    private FollowState followState;     // NONE, FOLLOWING, FOLLOWED_BY, MUTUAL

    public void follow() { ... }         // NONE|FOLLOWED_BY -> FOLLOWING|MUTUAL
    public void unfollow() { ... }
}
```

This is the thinnest of the five aggregates — most of `user`'s legacy surface (36 DTOs per the audit) is genuinely read-only lookup of data HelloTalk owns, not business logic this BFF should be modeling deeply. `follow()`/`unfollow()` are the only real state transitions; everything else (profile bundle, stats, limitations, batch status, presence) is a **Query**, not domain behavior, and is modeled as read-only projections assembled by the application layer's query handlers, not as aggregate methods. This resolves the audit's "36 DTOs collapse to 8-10 shapes" finding (`user.md` improvement #1) by making the **domain model** the single canonical shape (`UserProfile` + `ProfileSummary` value object), while the 8-10 *response* shapes the audit counted become API-layer projection DTOs mapped from the one domain shape — the multiplicity was a wire-format concern, not a domain-modeling one, and belongs in `api/dto` + `api/mapper`, not in `domain/model`.

## Domain service (no aggregate — stateless)

### `TranslationService`

```java
public interface TranslationService {
    TranslationResult translate(TranslationRequest request);
}
```

No identity, no state, no consistency boundary — a pure function over Value Objects. The audit's `translate.md` already confirms the legacy `TranslateClient`/`HtTranslateClient` port-and-adapter split is correctly designed; this design keeps that shape exactly, just relocates the port to `application.port.out.TranslationUpstreamPort` and the domain-facing service to `domain.service.TranslationService` (thin orchestration, no business rule beyond "encrypt, call, decrypt" — which is why it's a service, not an aggregate).

## Value objects

Replacing primitive obsession identified implicitly throughout the audit (bare `String cname`, `long userId`, `int busiType` passed everywhere with no compile-time distinction between e.g. a `userId` and a `hostId`, both `long`):

| Value Object | Wraps | Why |
|---|---|---|
| `Cname` | `String` | The room-channel identifier. Prevents accidentally passing a different string (e.g. a user-facing nickname) where a channel id is expected. |
| `RoomUserId` | `long` | A HelloTalk user id, as seen from inside a room context. |
| `HostId` | `long` | A room's host — distinct type from `RoomUserId` so `assignStageSeat(RoomUserId, HostId)` can't have its arguments accidentally swapped (a real bug class in the legacy code, where both are bare `long`). |
| `ManagerId` | `long` | Distinct from `RoomUserId` for the same reason — a manager action's *actor* should not be confusable with its *target*. |
| `BusiType` | `int` (1/2 today) | **Left as a value object wrapping `int`, NOT promoted to an enum** — the audit never confirmed the full set of upstream `busi_type` values beyond the 2 observed (voice=1, live=2 inferred from controller path segments), and prematurely enforcing an exhaustive enum risks silently rejecting a valid future upstream value. The value object still buys type-safety (can't confuse `busiType` with an unrelated `int`) without the closed-set risk. Revisit as an enum once a definitive upstream value list is confirmed (see `09-technical-risks.md`). |
| `RewardItem` | (existing) | Reused as-is from `com.jilali.platform.models.RewardItem` (Refactor 3) — no re-declaration inside the new domain model. |
| `MicState` | 4 legacy booleans (`isOnMic`, `isTurnOnMic`, `isTurnOnCam`, `isRaiseHand`) | `sealed interface MicState permits Off, Listening, Speaking, PendingApproval` — replaces 4 independent booleans (which legally allow nonsensical combinations like `isOnMic=false, isTurnOnMic=true`) with one closed, always-valid state. This is the single clearest "reduce primitive obsession, use sealed types" win the redesign delivers. |
| `RoomLifecycleState` | (implicit, not modeled at all in legacy) | `enum { CREATED, LIVE, ENDED }` — the legacy code has no explicit room-lifecycle state at all (it infers "ended" from the absence of a channel in list responses); making it explicit lets `Room.end()` guard against double-ending. |

## Domain events

```java
public sealed interface RoomEvent {
    record MemberJoined(Cname cname, RoomUserId userId) implements RoomEvent {}
    record MemberLeft(Cname cname, RoomUserId userId) implements RoomEvent {}
    record StageSeatTaken(Cname cname, RoomUserId userId, int seat) implements RoomEvent {}
    record StageSeatVacated(Cname cname, RoomUserId userId) implements RoomEvent {}
    record RaiseHandQueued(Cname cname, RoomUserId userId) implements RoomEvent {}
    record RaiseHandApproved(Cname cname, RoomUserId userId, ManagerId approver) implements RoomEvent {}
    record MemberKicked(Cname cname, RoomUserId target, ManagerId actor) implements RoomEvent {}
    record ManagerGranted(Cname cname, RoomUserId target, HostId by) implements RoomEvent {}
    record ManagerRevoked(Cname cname, RoomUserId target, HostId by) implements RoomEvent {}
    record CommentPosted(Cname cname, Comment comment) implements RoomEvent {}
    record RoomEnded(Cname cname, HostId by) implements RoomEvent {}
}
```

**Relationship to the existing `ImRealtimeEvent`/`RoomRealtimeEvent` sealed interfaces** (legacy `im.dto`/`realtime.dto`, both already correctly designed per the audit): those are **wire-level events** — the exact shape HelloTalk's upstream WebSocket pushes. `RoomEvent` above is a **domain-level event** — what the `Room` aggregate itself considers meaningful. The infrastructure layer's `RoomEventTranslator` (in `infrastructure.websocket`) converts one to the other in both directions:

- Inbound: upstream push (`RoomRealtimeEvent.StageJoin`, etc.) → domain command (e.g. a `StageSeatTaken` fact, applied to the in-memory `Room` projection) → republished as `RoomEvent.StageSeatTaken` via `ApplicationEventPublisher`.
- Outbound: `Room.assignStageSeat(...)` returns a fact → published as `RoomEvent.StageSeatTaken` → an `@EventListener` in `infrastructure.websocket` maps it to the Angular-facing WebSocket push shape.

This event translation is exactly what replaces the legacy `Sinks.Many` pub-sub (see `00-architecture-overview.md`'s Micronaut section) with `ApplicationEventPublisher`/`@EventListener`.

## Policies (extension points, not currently enforced)

```java
public interface ManagerAuthorizationPolicy {
    boolean canManage(RoomUserId actor, Cname cname);
}

/** Default: permissive, matches current (unchecked) legacy behavior exactly.
 *  Per explicit user instruction, authorization is out of scope for this
 *  redesign — HelloTalk's own backend is considered responsible for it, not
 *  this proxy. This interface exists ONLY so a future decision to add
 *  authorization has a clean seam, without requiring a redesign at that
 *  point. */
public final class PermissiveManagerAuthorizationPolicy implements ManagerAuthorizationPolicy {
    @Override public boolean canManage(RoomUserId actor, Cname cname) { return true; }
}
```

Same shape for `StageCapacityPolicy` (currently: legacy code has no explicit seat-count check either — it trusts upstream's own rejection; the new `Room.assignStageSeat` can either mirror that trust-upstream behavior for parity, or add a client-side pre-check as a UX nicety, non-authoritative — the latter is a nice-to-have, not required for parity, see `10-compatibility-considerations.md`).

## Repository contracts (upstream-facing ports, not database repositories)

See `05-port-definitions.md` for the full list — flagged here because it's a core domain-modeling decision: `domain.repository` in this bounded context does not mean "load/save from a local database." Every repository port is actually a read/write port to HelloTalk's own backend. `Room`, `RoomCommentThread`, etc. are **not persisted locally** — they are reconstructed on demand from upstream calls (exactly as the legacy code already does, just without an explicit domain layer naming that behavior). This is why the target package tree's `infrastructure/persistence/` sub-package will be near-empty for this context (see `06-package-dependency-analysis.md`).
