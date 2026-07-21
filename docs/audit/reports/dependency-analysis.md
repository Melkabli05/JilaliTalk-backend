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
