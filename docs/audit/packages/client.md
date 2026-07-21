# `com.jilali.client` — HTTP client layer to HelloTalk upstream

## Purpose

The infra/adapter layer that talks HTTP to HelloTalk's private mobile API. Holds both declarative `@Client` interfaces (Micronaut's typed HTTP-client proxy pattern) and an imperative gateway wrapper used when the upstream call needs custom headers, error unwrapping, or fan-out.

## File responsibilities (5 files)

| File | One-line summary |
|---|---|
| `JilaliClient.java` | The **single big** declarative `@Client` interface — 50+ methods, every upstream call site across every feature lives here as a typed proxy method. **Likely God Interface** (the audit flagged its layered import-of-feature-DTOs as the central architectural problem of the codebase, see `architecture.md` §4). |
| `JilaliResponses.java` | Helper for unwrapping the `{code,msg,data}` envelope upstream returns — used by every controller/service after an upstream call. |
| `ProfileClient.java` | A small, focused `@Client` interface for profile-specific endpoints (kept separate to give ProfileBundleService a narrower surface than `JilaliClient` provides — good factoring). |
| `VipExperienceCardClient.java` | Another small, focused `@Client` interface, only for the VIP card endpoints. Good factoring. |
| `JilaliGateway.java` | The imperative `@Client("jlhub") HttpClient`-injected gateway — wraps select upstream calls with custom headers (`AuthTokenHolder`, per-user session cookie tier), envelope unwrapping, and result mapping. The second tier of the auth ladder (`auth.SessionAuthClientFilter` first, this as fallback). |

## Dependencies

- **Inbound** (this package depends on): **7 feature packages** — `comment.dto`, `manager.dto`, `room.dto`, `signin.dto`, `stage.dto`, `user.dto`, `vip.dto`. Plus `core`, `crypto`. Per the architecture doc, this is the central circular-dependency culprit.
- **Outbound**: none — this IS the lowest wire layer in the BFF.

## ⚠ Top-three findings (per the architecture and per-package audits)

1. **Circular dependency with 7 feature packages** — `client/JilaliClient.java` etc. import feature-DTOs to use them as method signatures' parameter/return types. Meanwhile the feature packages import `client` to invoke the proxy methods. **This is the #1 blocker to a hexagonal-architecture rewrite** and the priority refactor.
2. **Two HTTP-client invocation styles coexist**: 4 declarative `@Client` interfaces (`JilaliClient`, `ProfileClient`, `VipExperienceCardClient`, `HtTranslateClient`) versus 2 files doing imperative `@Client("jlhub") HttpClient` injection (`HelloTalkAuthClientImpl`, `JilaliGateway`). Should be normalized to one style — likely declarative, with the imperative-only needs (per-call custom headers) pushed to a Micronaut `@Client` filter or a dedicated imperative fallback.
3. **`JilaliClient` is a God Interface** — too many methods across too many feature areas in one Java interface. Should be split per feature, following the already-existing pattern of `ProfileClient`/`VipExperienceCardClient` (one feature → one interface).

## Improvement opportunities

1. **High**: split `JilaliClient` into per-feature sub-interfaces (`RoomClient`, `StageClient`, `ManagerClient`, `CommentClient`, `SigninClient`, `TranslateClient`, `VipClient`, …) — each in the same feature package as its DTOs. Move existing focused interfaces (`ProfileClient`, `VipExperienceCardClient`) into the same package as their feature-DTOs.
2. **High**: lift the client's *current* DTO dependencies off feature packages into a shared `com.jilali.platform.upstream-dtos` package, or — preferred — push wire DTOs entirely into the `client` package (since they ARE the wire shape), and have feature code map them to feature-DTOs at the seam.
3. **Medium**: normalize imperative vs declarative HTTP-client usage across the codebase.
4. **Low**: `JilaliResponses.unwrap` is the single envelope-unwrap utility — verify it's used everywhere (a `grep` is in the per-file doc). Stragglers that unwrap ad-hoc should be migrated.
