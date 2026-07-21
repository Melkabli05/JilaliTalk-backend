# JilaliEnvelope

`src/main/java/com/jilali/core/JilaliEnvelope.java`

## Purpose
A `@Serdeable` generic record mirroring the upstream's universal response envelope `{"code":..,"msg":..,"data":..}`. Internal-only contract: unwrapped by client-layer callers, never leaked to the frontend.

## Responsibilities
- Model the upstream envelope fields.
- Provide `isSuccess()` (code == 0).

## Public API
- `record JilaliEnvelope<T>(int code, @Nullable String msg, @Nullable T data)`
  - `code: int` — non-null primitive; upstream status code.
  - `msg: String` — `@Nullable`.
  - `data: T` — `@Nullable`; payload of caller-specified type.
- `boolean isSuccess()` — true when `code == 0`.

## Dependencies
- Micronaut: `@Serdeable`, `@Nullable`.
- Depended on by: `JilaliResponses`, `VipExperienceCardClient`, `JilaliClient`.

## Coupling and cohesion analysis
High cohesion — a pure generic DTO with one semantic helper. Coupling minimal and healthy (client layer only). Correct place (core) for a shared wire contract.

## Code smells
- **Data Class** — by design and appropriate (DTO).
- **Primitive Obsession** on `code` (raw int) — acceptable for a wire record; `isSuccess()` mitigates by encapsulating the magic `0`.

## Technical debt
- None material.

## Duplicate logic
None. Distinct contract from `ApiError` (inbound upstream envelope vs. outbound problem+json).

## Dead or unused code
None. `isSuccess()` used by client callers; record is `@Serdeable` (framework).

## Refactoring recommendations
- None urgent. Optionally add a helper to unwrap `data` or throw `JilaliException.fromCode(code, msg)` on failure, since callers repeat that pattern — but verify the call sites (`JilaliResponses`, `JilaliClient`) before centralizing.
