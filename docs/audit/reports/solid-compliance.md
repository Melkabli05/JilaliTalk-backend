# SOLID Compliance Report

> Per-principle evaluation of `jilalibff`. Per-package detail in `docs/audit/packages/*.md`; specific file references in `docs/audit/files/**/*.md`.

## S — Single Responsibility Principle

### Violations (God Classes / Packages)

| Class / Package | Why it violates SRP | Phase to fix |
|---|---|---|
| `client/JilaliClient` | 50+ methods covering room, stage, manager, comment, signin, vip, translate, user concern groups — one interface, many reasons to change. | Phase 1 |
| `client/JilaliClient` (single huge interface) + `client/ProfileClient` + `client/VipExperienceCardClient` | The split is *inconsistent* — some concerns factored, others packed in. | Phase 1 |
| `room/RoomController` | Largest controller in the codebase; endpoint count exceeds typical threshold for a single class. | Phase 2 |
| `manager/ManagerController` | Same — too many action endpoints in one class, plus the missing-authorization bug. | Phase 1 (security) + Phase 2 (split) |
| `stage/StageController` | Same — 8+ endpoints, no auth checks. | Phase 1 + Phase 2 |
| `vip/VipExperienceCardController` | Same — multiple operations, no auth checks. | Phase 1 + Phase 2 |
| `core/` package as a whole | Filters, JWT, errors, JSON utilities, WebSocket helpers — too many reasons to change. | Phase 3 |
| `core/JilaliGateway` | Mixes imperative HTTP-client invocation, envelope unwrapping, and result-mapping for many feature domains. | Phase 3 |

### Good SRP examples

- `core/ApiError.java` — single shape (error payload records).
- `crypto/` package — every class implements one cipher cleanly. The package as a whole isn't cohesive, but each class is.
- `im/HtImFrameDecoder.java` — sealed-interface dispatch on cmd ids; one per switch case.
- `comment/EncryptedFieldCodec.java` — single method pair (encrypt/decrypt).

---

## O — Open/Closed Principle

### Where the codebase is closed-for-modification

- **`HtImNotifyMapper`** and **`HtNotifyMapper`** use big `switch (msg_type)` statements with hand-written per-case mappings → adding a new msg_type requires editing these (open-to-extension via switch-statement-with-default is the Java-idiomatic tradeoff, OK).

### Where it should be open

- **`HtImNotifyMapper` per-case `mapText`/`mapImage`/etc. methods** could each be a separate `MapMsgType` strategy implementation, selected by a small registry, so adding a new msg_type doesn't require editing the central switch. Same for `HtNotifyMapper`. **Phase 3.**
- **`textOr`/`nullableText`/`nullableAny`** in `HtImNotifyMapper` are hardcoded utility helpers — should move to a shared `com.jilali.im.mapper.support` if any second caller arrives, but currently only one caller, so YAGNI.

### Sealed-interface "open-for-extension" wins

- `im.dto.ImRealtimeEvent`, `realtime.dto.RoomRealtimeEvent`/`RoomCcRealtimeEvent`, `auth.LoginOutcome`/`SignupOutcome`, `im.HtImFrameDecoder` are **well-designed** sealed interfaces where adding a variant forces compile-time review (exhaustive switch). This pattern should be the target architecture's default for any new "one of N typed values" model.

---

## L — Liskov Substitution Principle

### Concrete violations

- **`JilaliClient` interface** is implemented by no concrete class in `client/`; features call it via Micronaut's compile-time proxy. That's fine; the risk is that any feature's interface method could in principle be implemented differently (the proxy is shared), but since there's only one impl this is theoretical.

### Worth flagging

- `auth.LoginOutcome` and `auth.SignupOutcome` are sealed interfaces with distinct subtypes per documented outcome. **Every subtype preserves the interface contract** (no surprising return types) — good.

---

## I — Interface Segregation Principle

### Major violations

- **`JilaliClient`**: as above, 50+ methods = many clients (per feature) each forced to depend on the full surface. Splitting is mandatory.
- **`user.dto.UserInfo`**: bulk "user-shaped" record carrying every possible user field; every call site that only needs a subset is forced to depend on fields they ignore. **Split along the same boundary as `JilaliClient`.**

### Good ISP examples

- `client/ProfileClient` and `client/VipExperienceCardClient` already exist as focused sub-interfaces — they demonstrate the right ISP pattern. Generalize.
- `auth.HelloTalkAuthClient` is narrow (login + signup helpers).

---

## D — Dependency Inversion Principle

### Violations

- **Circular `client ↔ 7 feature packages` dependency** is the worst DIP violation. The "low-level" wire layer is depending on "high-level" feature types. The fix IS a hexagonal restructure — see the target-structure report.
- **`JilaliGateway` directly imports feature DTOs** — same violation, same fix.
- **`comment/CommentController.toDto`** depends on the upstream wire-DTO concrete class `Comment`, mapping it field-by-field to its own `CommentDto`. A proper inversion would have the wire-DTO implement a domain-interface and the controller depend on that interface.

### Good DIP examples

- `client/TranslateClient` (port) + `client/HtTranslateClient` (concrete adapter) is the textbook DIP setup — both the unrelated-feature callers AND the test suite depend on the port, not on the concrete adapter. Generalize.
- `auth.HelloTalkAuthClient` (interface) + `auth.HelloTalkAuthClientImpl` (impl) is the same pattern, applied correctly.

---

## Summary table

| Principle | Status | Severity |
|---|---|---|
| SRP | Mixed — many small classes, but several God Classes (`JilaliClient`, `RoomController`, package-level `core`) | High |
| OCP | Mostly fine, switch-statement conventions are the trade-off | Low |
| LSP | Clean (sealed interfaces, single-impl declarations) | Low |
| ISP | Violated by `JilaliClient` (50+ methods) — fix it | High |
| DIP | Violated by circular `client ↔ feature-DTOs` — top priority | Critical |

## What the rewrite should change

1. **ISPs everywhere**: every public interface should be a narrow contract; concrete adapters (with their methods spread thin) live behind the port.
2. **No circular deps**: enforce `client` (port) + `features` (consume ports) + `platform` (shared types) only — no feature-DTO ever imported back into `client`.
3. **Sealed interfaces by default** for any "one of N" type.

What the rewrite should NOT change: the per-class discipline of single-responsibility is already good at the leaf level. The violations are all at the **package / interface boundary** level — the same fix (split big interfaces into feature-aligned sub-interfaces) addresses both SRP and ISP violations simultaneously.
