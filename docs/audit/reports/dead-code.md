# Dead / Removable Code Report

> Confirmed via Grep across `src/main/java` (no callers) or by the per-file / per-package audit agents. **Framework-invoked code (annotated `@Controller`/`@ServerWebSocket`/`@Client`/`@EventListener`/`@Cacheable`/Jackson-serialized record accessors) is NOT dead** even when no explicit callers exist. The list below is conservative.

## Confirmed dead (and removed during the refactor)

| File / Field | Evidence | Action | Status |
|---|---|---|---|
| `user/dto/VisitRequest.java` | Grep-verified: no callers of `VisitRequest` in `src/main/java` (per the partial user-dto audit agent's note). | **Deleted** (Refactor 1). | ✅ removed 2026-07-21 |
| `auth/LoginResponse.UserInfo` fields `areaCode`, `regTs`, `isAdult`, `isNewRegUser`, `isVip` | Populated by Jackson from upstream, never read anywhere in `src/main/java` (only `userId()` and `jwt()` are consumed) — per the auth-package audit agent. | **Deleted** (Refactor 2). | ✅ removed 2026-07-21 |

## Suspect but not confirmed dead (verify before deleting)

These are flagged in per-package docs as candidates but each needs a 5-minute grep verification:

| Item | Why suspicious | How to verify |
|---|---|---|
| `core/SnakeToCamelJson.java` | May be redundant with `CamelCaseResponseFilter`. | `grep -rn "SnakeToCamelJson" src/main/java` — count callers. |
| `core/ws/SequentialSender.java` | Helper used by connectors; low concern. | `grep -rn "SequentialSender" src/main/java` — count callers. |
| `core/ws/HeartbeatPump.java` | Helper used by connectors. | Same. (Will likely be removed/replaced during consolidation per Micronaut-adoption report.) |
| Any `*Request` test-only DTO fields with `@JsonIgnore` | None yet identified. | Verify `@NotNull`-marked fields vs all greps for use. |

## Likely-dead-with-confidence (per audit agents)

- **`VisitRequest`** (above) — confirmed.
- **`areaCode`/`regTs`/`isAdult`/`isNewRegUser`/`isVip` on `LoginResponse.UserInfo`** — confirmed by exhaustive grep.

## Reported dead but considered alive

These were flagged by the audit framework-invoked code consideration:

- `JwtUtil.uidFromBearer` — accessor used reflectively by Jackson, not by direct calls.
- `HashMap`-backed getters on sealed-interface records — used by polymorphic JSON serialization.
- `@ClientFilter`/`@RequestFilter` methods on `DefaultHeadersClientFilter`, `JilaliErrorResponseFilter`, `CamelCaseResponseFilter`, `HeaderPropagationFilter` — framework-invoked.
- `@EventListener` methods in `auth/` — framework-invoked.
- `@Controller` endpoint methods — these ARE the callers; they don't have callers in `src/main/java`, they're called by the HTTP server.

## Resist the temptation to "clean up" proactively

A common audit finding: "DTO has 14 fields, 5 of which look unused, remove them!" This is wrong because:
- They are populated by Jackson from the upstream wire payload even if no code currently reads them.
- Removing them would silently break wire compatibility.
- Adding them back later requires a HelloTalk upstream-shape update.

The only safe removals are those verified by Grep that no Jackson-deserialization consumer reads. The two confirmed-dead items above (`VisitRequest` overall, the 5 never-read `LoginResponse.UserInfo` fields) are the safe starting points.

## Other non-dead, but worth-considering-for-removal items

- **Phase 2 architectural refactor** will move/rename/split many files; some may be deleted in the process (e.g. one of `TranslateClient` / `HtTranslateClient`, if the port-and-adapter pattern is kept the two stay; if collapsed, only the client remains).
- **The wrapper class `JilaliResponses.unwrap`** is thin enough that someone may propose deleting it in favor of inline unwrapping — DON'T, it's the single source of envelope-shape assumptions across the codebase.
- **`ProfileBundleService` `*`-shaped methods**: verify each method body — some may be trivial pass-throughs that can be inlined.

## File-level legacy (not dead, but no longer the right home)

These will MOVE during the rewrite, not be deleted:

- `core/SnakeToCamelJson` → `platform.json`
- `core/ws/*` → `platform.websocket`
- `core/DefaultHeadersClientFilter` + `core/HeaderPropagationFilter` → `platform.http.filters`
- `core/GlobalErrorHandler` + `core/JilaliException` + `core/ApiError` + `core/JilaliErrorResponseFilter` → `platform.errors`
- `core/JwtUtil` + `core/UidExtractor` → `platform.security.jwt`
- `core/JilaliProperties` → `platform.config`
- `core/AuthTokenHolder` → `platform.auth.token`

The MOVE itself is benign (single file, no behavior change) — what changes at MOVE time is that features no longer have a generic "core" to lean on, only well-named `platform.*` sub-modules.
