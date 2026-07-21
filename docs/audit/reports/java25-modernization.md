# Java 25 Modernization Opportunities

> Where `jilalibff` already uses modern Java features, where it doesn't, and concrete migration steps. Detail in per-file docs.

## What's already there (good)

| File | Modern Java feature already in use | Notes |
|---|---|---|
| `room/RoomJoinService.java` | `StructuredTaskScope` (Java 25 preview) for parallel fan-out + automatic cancellation on any sub-task failure | Correct usage. |
| `signin/SigninController.java` (`roomLevelBundle`) | `StructuredTaskScope` | Same pattern as `RoomJoinService`. |
| `im/dto/ImRealtimeEvent.java`, `realtime/dto/RoomRealtimeEvent.java` + `RoomCcRealtimeEvent.java`, `auth/LoginOutcome.java` + `SignupOutcome.java`, `im/HtImFrameDecoder.java` | `sealed interface` (Java 25 standard) with explicit `permits` | The single best-discipline pattern in the codebase; should be the default for any new "one of N" type. |
| All `im.dto` records etc. | `record` for DTOs | Standard. |
| Build config (`build.gradle`) | `JavaVersion.toVersion('25')` + `--enable-preview` | Confirmed in use. |

## Concrete modernization opportunities

### Pattern matching for switch (Java 25 standard)
- **Where**: `HtImNotifyMapper.map(...)` (the `switch` on `root.path("msg_type")` chains), `HtImFrameDecoder.handleFrame` (the cmd-id dispatch), `HtImUpstreamConnector.handlePacket`.
- **What**: replace `switch (string)` with `switch (root.path("msg_type").asText())` using Java 25's pattern matching for switch on the resulting sealed type, OR refactor from string-keyed dispatch to sealed-interface dispatch (see § below).
- **Why**: Java's exhaustive-switch-check on sealed types is compile-time verified — adding a new variant is caught at compile time, not silently at runtime.

### Sealed-interface + record-pattern dispatch for msg_type
- **Where**: `HtImNotifyMapper.map` is a stringly-typed switch on `msg_type`. The natural replacement is a small sealed interface `JsonMsg` with per-`msg_type` subtypes (TextMsg, ImageMsg, GiftMsg, etc.) and a single `sealed switch` consumer.
- **Benefit**: same as pattern matching; also opens the door to a generic mapper, eliminating the per-case methods.

### `record` patterns in switch (Java 25 standard)
- **Where**: any future switch on a sealed record union (instead of `instanceof` chains).
- **Benefit**: concise, exhaustive.

### Virtual threads (Java 25 standard for `Thread.ofVirtual`)
- **Where**: `htIm/HtImUpstreamConnector` and `realtime/HtLiveHubUpstreamConnector` already use `CompletableFuture` chains for the reconnect/reconnect-retry pattern; `RoomJoinService` uses `StructuredTaskScope` (which spawns virtual threads under the hood).
- **What**: convert remaining manual `CompletableFuture.runAsync(..., delayedExecutor(...))` uses to `StructuredTaskScope` with `fork`, OR `Executors.newVirtualThreadPerTaskExecutor()` where plain thread-per-task is fine.
- **Benefit**: simpler code, scales naturally to many concurrent upstream connections.

### Records everywhere DTOs are simple value shapes
- **Where**: a handful of non-record classes still exist (verify via grep `@JsonSubTypes.*\.class` vs `record`). The major DTO-using files are already records.
- **What**: any remaining mutable POJO-style DTO → record.
- **Benefit**: immutability, less boilerplate, automatic `equals`/`hashCode`.

### Sealed interfaces in `core/ApiError` and `core/JilaliException`
- **Where**: error reporting. A sealed `core.JilaliError` with named subtypes could compose with `LoginOutcome`/`SignupOutcome` patterns — also unifies the two error paths the audit flagged as "competing mechanisms."
- **Benefit**: one error shape across the whole BFF.

### `instanceof` patterns replaced with pattern matching
- **Where**: `realtime.dto.RoomCcRealtimeEvent` (per-event-type switches), `HtImNotifyMapper`'s per-type helpers.
- **What**: Java 25 `instanceof`-pattern destructuring: `if (obj instanceof TextMsg t) { return map(t); }`.
- **Benefit**: less ceremony, more type-safety.

### Compact constructor validation (records only)
- **Where**: some request DTOs have `jakarta.validation.constraints` annotations; records support compact-constructor validation. **No changes needed**, but worth noting: at present, validation runs at deserialization time but record construction is unvalidated — if malformed input arrives, you fail later. The current convention is OK because it relies on `micronaut-validation` activation.

### `sealed` + `record` for the request DTO clusters (stage, manager)
- See the `technical-debt.md` report — stage and manager `*Request` DTOs collapse to a Java 25 sealed interface cleanly:
  ```java
  public sealed interface StageAction
      permits StageAction.RaiseHand, StageAction.Kick,
              StageAction.Invite, .Approve { ... }
  ```
  Polymorphic dispatch via `switch` becomes exhaustive.

---

## What is NOT worth doing (YAGNI)

- Replacing the existing manual `core/ws/ExponentialBackoff` with a Java-25 stdlib backoff helper — none exists. Keep the custom helper but possibly simplify once it has only one caller (post-`im`/`realtime` consolidation).
- Re-doing `auth/SessionAuthClientFilter`'s exception-handling in switch patterns — the filter doesn't use instanceof chains anyway.
- Virtual-thread conversion of `realtime/HtLiveHubUpstreamConnector`'s blocking I/O reads — only worth doing if profiling shows the per-connection thread blocks matter. The existing virtual-thread-friendly code paths (`RoomJoinService`/`SigninController.roomLevelBundle`) cover the obvious cases.

---

## Summary

Most Java-25 modernization wins come from **eliminating dispatch-by-string** (the codebase's primary remaining low-quality pattern) in favor of sealed-interface exhaustive switches. The current StructuredTaskScope and sealed-interface usage is good — generalize it. Virtual threads are partially used; complete the spread. Do NOT do speculative language-feature refactors that don't pay for themselves.
