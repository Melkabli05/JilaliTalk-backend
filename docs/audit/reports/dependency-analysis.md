# Package Dependency Analysis

> Inter-package import graph for `jilalibff`. Per-file evidence in `docs/audit/files/**/*.md`.

## Top-level package inventory (14 packages)

| Package | Files | Role |
|---|---|---|
| `auth` | 27 | Browser-facing auth (login/signup/sessions) + per-session cookie-based identity |
| `client` | 5 | Wire-layer HTTP clients to HelloTalk upstream |
| `comment` | 11 | Comment + caption feature |
| `core` | 17 (+ws 3) | Cross-cutting infrastructure: filters, errors, JWT, the live-mutable token holder |
| `crypto` | 8 | Reverse-engineered HelloTalk ciphers |
| `im` | 9 (+dto 1) | Personal IM WebSocket channel |
| `manager` | 6 | Voice-room manager-role feature |
| `realtime` | 7 (+dto 2) | Voice-room (LiveHub) channel — structurally parallel to `im` |
| `room` | 28 | Voice-room lifecycle |
| `signin` | 9 | Daily check-in feature |
| `stage` | 11 | Voice-room stage mechanics (raise-hand, kick, invite) |
| `translate` | 10 (+codec +dto) | AI translation via encrypted microservice |
| `user` | 39 | Lookups of OTHER HelloTalk users' profiles |
| `vip` | 12 | VIP experience-card trial/gifting |

## Direct dependency map (imports)

```
core          → (none)
crypto        → (none)
auth          → client, core, crypto, user
client        → comment.dto, core, crypto, manager.dto, room.dto, signin.dto, stage.dto, user, vip  [⚠ CIRCULAR with each]
comment       → client
im            → auth, client, core, crypto, user
manager       → client
realtime      → core
room          → client, comment, core, realtime, stage, user
signin        → client, room, room.dto (cross-package — see duplication report)
stage         → client, core
translate     → core, crypto
user          → client, core, crypto, room
vip           → client
```

## Circular dependencies (confirmed by direct grep)

### CRITICAL — `client` ↔ 7 feature packages

`client/` (specifically `JilaliClient.java`, `JilaliGateway.java`, `ProfileClient.java`, `VipExperienceCardClient.java`) imports DTOs from `comment.dto`, `manager.dto`, `room.dto`, `signin.dto`, `stage.dto`, `user.dto`, `vip.dto` — i.e. from EVERY feature package's DTO sub-package.

Conversely, every one of those feature packages imports `com.jilali.client.*` to invoke the HTTP client.

This is a **true circular dependency** in the source-code sense: feature packages cannot compile without `client`, and `client`'s `@Client` method signatures cannot be resolved without the feature DTOs.

Severity: **CRITICAL** — top blocker to a hexagonal architecture rewrite.

Fix: split `JilaliClient` into per-feature sub-interfaces in each feature package; move wire-only DTOs that ARE upstream-response shapes (and only those) into a shared `com.jilali.platform.upstream` package; both `client` ports and feature code depend on the shared wire DTOs downward.

**Scope/sequencing note (evaluated during the refactor pass, not yet executed)**: this diagnosis holds up — grep-verified 8 feature packages import `com.jilali.client.*`, and `client`'s own `@Client` interfaces import DTOs back from 7 of them. It's a real cycle in the acyclic-dependencies sense (adapter depending upward on feature-specific types). However, the blast radius is larger than any refactor shipped so far in this pass: **7 distinct classes inject `JilaliClient` directly** (`RoomController`, `ManagerController`, `SigninController`, `JilaliGateway`, `RoomsSearchService`, `RoomJoinService`, `CommentController`), and at least two of them (`RoomJoinService`, `SigninController`) drive delicate `StructuredTaskScope`-based parallel fan-out across *multiple* feature-area calls in a single method (e.g. `RoomJoinService` calls `client.liveRoomInfo`/`voiceRoomInfo` (room), `client.stageList` (stage), `client.roomUserList` (user), and `client.comments` (comment) inside one fan-out). Splitting `JilaliClient` means every one of those 7 call sites needs to juggle multiple injected client interfaces instead of one, in code whose correctness depends on subtle cancellation/exception semantics.

Also worth weighing: unlike a stateful service, `JilaliClient` is a **pure declarative `@Client` interface — no fields, no behavior, no static initialization** — so the "cycle" doesn't create any of the classic problems circular dependencies cause elsewhere (no init-order hazard, no blocked unit testing — any caller can already mock the interface regardless of which package its DTOs live in). The severity is real from a *package-topology* standpoint (a future extraction of any single feature into its own deployable module would drag the other 7 features' DTOs along via `client`), but low from a *day-to-day maintainability* standpoint.

**Decision**: defer full execution to its own dedicated pass rather than attempt it inside this refactor session. The prior attempt in this same session to mechanically restructure the `WebSocketConnectionLifecycle` migration across two files in one sitting produced broken code (stray dead branches, an injected placeholder method) and had to be reverted — that was a *2-file* change; this one touches 7+ files including the two most fan-out-heavy services in the codebase. Attempting it under the same time pressure risks the same failure mode at higher stakes. Recommended approach when it IS picked up: one feature slice at a time (e.g., extract just the `comment`-area methods — `comments`/`sendComment`/`captionHistory`/`captionSwitch` — into a `comment.CommentJilaliClient` sharing the same `@Client(id="jlhub", path="/livehub")` id/path, verify `CommentController` and `RoomJoinService` compile and pass tests, commit, then repeat per feature) — never all 8 feature slices in a single commit.

## Hidden "downward" violations

Every feature package imports `com.jilali.client` and uses it as an outgoing adapter — this is the correct direction (feature→adapter). The violation is on the adapter side: `client` imports feature DTOs, which makes the adapter layer depend UPWARD on feature-specific types.

The "right" direction (everything depends on `core` and `crypto` and `platform`; never the reverse) cannot be enforced with the current package layout.

## Target structure enforces this

`com.jilali.platform` (cross-cutting): `auth-token`, `errors`, `cryptography`, `websocket-support`, `validation`, `upstream-dtos`. NOTHING in features imports from features; features only import from `platform` and from `application` (which is itself a thin coordinator).

## Implication: the `core` package, despite being the "lowest" today, is still a mixing-pot

It holds filters, errors, JWT, token holder, WebSocket helpers, and JSON utilities. In the target structure, it splits into the `platform.*` sub-packages listed above. The functional outcome: a feature package only ever depends on (1) `platform.*` and (2) other feature packages below it (which is ZERO — features are siblings, not stacked).

## Per-package quantitative dependency profile (count of imports FROM other packages)

| From | To packages | Count |
|---|---|---|
| `auth` | 4 distinct | moderate |
| `client` | 8 distinct (high — and includes 7 feature packages, the circular issue) | highest |
| `comment` | 1 (client) | lowest |
| `core` | 1 (crypto) | lowest |
| `crypto` | 0 | zero |
| `im` | 4 distinct (auth + client + core + crypto + user) | moderate |
| `manager` | 1 (client) | lowest |
| `realtime` | 1 (core) | lowest |
| `room` | 6 distinct (client + comment + core + realtime + stage + user) | high |
| `signin` | 2 (client + room) | low |
| `stage` | 2 (client + core) | low |
| `translate` | 2 (core + crypto) | low |
| `user` | 4 (client + core + crypto + room) | moderate |
| `vip` | 1 (client) | lowest |

The two outliers are **`client`** (8 dependencies, including 7 feature packages — the circular dep) and **`room`** (6 — the largest feature package, naturally touches everything related to room lifecycle, roster, stage, comment). Both are flagged.

The cleanest packages: `crypto`, `core` (1 dep), `comment`, `manager`, `realtime`, `signin`, `stage` (some have low counts; `signin` and `stage` cross package boundaries only for the upstream DTO clone issue noted in the duplication report), `translate`, `vip`. These mostly express "I depend only on a thin contract" and are the model for the target architecture.

## Summary

The fundamental shape problem is that `client` (which should be the LOWEST dependency) is in fact the HIGHEST-dependency package, because it's been used as a dumping ground for all wire-layer code AND it imports feature-DTOs as method signatures. Every feature package that wants to be its own bounded context is blocked.

The fix has two parts:
1. **Move wire-only DTOs from feature packages to `com.jilali.platform.upstream-*`** (alongside the new platform structure).
2. **Split `JilaliClient` into one interface per feature, in each feature's own package**.

Both changes are part of the target structure.
