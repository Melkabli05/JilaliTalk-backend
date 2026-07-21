# Architecture Assessment

> Cross-cutting evaluation of jilalibff's architecture quality. Detail in `docs/audit/architecture.md`; per-package context in `docs/audit/packages/*.md`.

## Overall rating

**Good foundation, strong accumulated complexity.** The codebase started as a competent single-account BFF and grew organically as the surface area of HelloTalk's private API expanded. Java 25 is used (with `--enable-preview`), Micronaut's compile-time DI is in use, `StructuredTaskScope` has been adopted in `RoomJoinService` and `SigninController`, sealed interfaces appear in the event-type hierarchies. All of that is encouraging.

What holds the codebase back is structural, not coding:
- **#1 — circular dependency between `client` and 7 feature packages** via feature-DTOs-as-method-signatures.
- **#2 — structural duplication between `im` and `realtime`** (~500 lines of parallel connector/event-source/socket-relay code).
- **#3 — DTO proliferation in `user` (36 files)** and the 28-field `Comment`/`CommentDto` duplicate.
- **#4 — three missing authorization gates** (`manager`, `vip`, likely `stage`) allowing any authenticated caller to act on any user's resources.

## Strengths

- ✅ Compile-time DI (Micronaut) eliminates reflection concerns.
- ✅ Sealed interfaces + Jackson polymorphic typing for event unions (`im.dto.ImRealtimeEvent`, `realtime.dto.RoomRealtimeEvent` + `RoomCcRealtimeEvent`, `auth.LoginOutcome`, `auth.SignupOutcome`, `im.HtImFrameDecoder`).
- ✅ `AuthTokenHolder` (live-mutable JWT holder) was added as a focused fix to a real correctness bug — not a large-scale speculative refactor. The whole backend now derives its upstream JWT from one place.
- ✅ `StructuredTaskScope` (Java 25 preview) is in use in `RoomJoinService` and `SigninController.roomLevelBundle` — concurrency primitives are modern.
- ✅ `JilaliClient` declarative HTTP-client interface pattern is correct, where it's used — the imperative `@Client("jlhub") HttpClient` injection in `HelloTalkAuthClientImpl`/`JilaliGateway` is the inconsistency, not the pattern itself.
- ✅ Cache-key discipline (per `TranslateService`'s Javadoc) is documented and correct for the single-account BFF design.
- ✅ Per-tab cancellation in `im` and `realtime` pub-sub (multicast + replay-state seam).

## Weaknesses (top 5)

| # | Weakness | Where | Impact |
|---|---|---|---|
| 1 | Circular `client ↔ feature-DTOs` dependency | `client/` package + 7 feature packages | **Cannot compile `client` in isolation; cannot establish a hexagonal boundary.** Top blocker to target rewrite. |
| 2 | `im` ↔ `realtime` structural duplication | `im/` + `realtime/` (15 files) | ~500 lines of parallel code, separate-bug surface. Both packages independently reimplement connect-WS-upstream + fan-out + relay. |
| 3 | DTO proliferation | `user/dto/` (36 files), `room/dto/` (23), `comment/dto/` (10) | Maintenance tax; one wire-shape change = touch many files. |
| 4 | Authorization gaps | `manager.ManagerController`, `vip.VipExperienceCardController`, likely `stage.StageController`, and `im.ImSendController` outbound-side | Any logged-in caller can promote/demote moderators, use/claim another user's VIP card, etc. — **CLAS** = broken trust boundaries. |
| 5 | Plaintext JWT at rest in H2 | `auth/JdbcAuthSessionRepository` | The HelloTalk upstream JWT issued to this account is stored unencrypted in `auth_session.jwt`. Filesystem read = live upstream credentials. |

## What the target architecture should preserve

- The **single-account, backend-owns-identity** design (it's a deliberate simplification, not accidental).
- The **hexagonal port-and-adapter** pattern as seen in `TranslateClient` (port) / `HtTranslateClient` (adapter) — generalize this, don't replace it.
- The **sealed-interface + polymorphic-typing** pattern for event unions (`ImRealtimeEvent`, `RoomRealtimeEvent`, `RoomCcRealtimeEvent`).
- The **StructuredTaskScope** + virtual-thread concurrency model already in `RoomJoinService`/`SigninController` — lean further into it during the rewrite.
- The **DTO-as-wire-shape** principle — don't invent a domain layer just to have more types; the BFF is thin enough that wire DTOs ARE the working data. But do introduce a clear DTO↔model seam at the boundary, not in every response.

## What must go

- The circular dependency (item 1) — top priority.
- The plaintext-at-rest JWT storage (item 5) — security-critical.
- The authorization gaps (item 4) — must be re-implemented even if not redesigned.
- The 3-package microservice-equivalent duplication between `im`/`realtime` — a target rewrite should produce ONE shared upstream-WebSocket-connector abstraction.
- The 36+ DTO sprawl in `user/dto/`, the 28-field `Comment`/`CommentDto` duplicate — collapse.

## What is acceptable as a conscious trade-off

- The `crypto` package — yes, all of it is "we should use a standard library" advice, but the standard libraries genuinely don't offer these algorithms. **Keep as-is.** Document explicitly so future contributors don't try to "improve" it.
- The HelloTalk upstream protocol is unofficial; treating the wire shape as authoritative and tolerating undocumented fields is correct — but treat any field names as DOCUMENTED but not PROMISED, and structure code so a single field rename doesn't require touching 12 files (this last point IS achievable in the rewrite).
- The fully-buffered SSE in `translate` is a real performance concern, but it's not a correctness bug — defer to a later phase.

## Verdict

**Architecture**: mature-component, immature-boundary. Solve the boundary problems first; the component-level patterns are already in good shape.

**Migration risk**: moderate. The shape of the rewrite is well-defined (port-and-adapter + feature-first packages + appropriate Micronaut-native usage), but the DTO sprawl and the im/realtime duplication are real refactoring surface, and a naive port to a new structure will leave the auth gaps in place. Sequence the rewrite so security-critical fixes (Phase 1) precede aesthetic refactors.
