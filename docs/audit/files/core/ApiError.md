# ApiError

`src/main/java/com/jilali/core/ApiError.java`

## Purpose
An RFC 9457 (`application/problem+json`) shaped error body record, hand-rolled to give the gateway full, dependency-free control over the error shape returned to the Angular frontend.

## Responsibilities
- Model a problem-details response body (`type`, `title`, `status`, `detail`, optional `upstreamCode`).
- Provide a convenience factory (`of`) that defaults `type` to `about:blank`.

## Public API
- `record ApiError(String type, String title, int status, String detail, @Nullable Integer upstreamCode)` — the record itself; `@Serdeable` for Micronaut serde.
  - `type: String` — URI reference identifying the problem type (non-null; caller supplies `about:blank` via factory).
  - `title: String` — short human-readable summary (non-null).
  - `status: int` — HTTP status code.
  - `detail: String` — occurrence-specific explanation (may be null in practice — see debt).
  - `upstreamCode: Integer` — `@Nullable`; original Jilali code when the failure originated upstream.
- `static ApiError of(int status, String title, String detail, @Nullable Integer upstreamCode)` — builds an `ApiError` with `type = "about:blank"`.

## Dependencies
- Imports: `io.micronaut.core.annotation.Nullable`, `io.micronaut.serde.annotation.Serdeable`.
- Depends on IT: only `GlobalErrorHandler` (all three nested handlers build `ApiError.of(...)` bodies). No other caller.

## Coupling and cohesion analysis
High cohesion — a pure data carrier with one factory. Coupling is minimal and healthy: only the error handler constructs it. Tightly paired with `GlobalErrorHandler` by design.

## Code smells
- **Data Class** — by design and appropriate here (it is a DTO). Not a problem.
- Minor **Primitive Obsession**: `status` is a raw `int` while the rest of the codebase uses `io.micronaut.http.HttpStatus`; the handler calls `.getCode()` to bridge. Acceptable for a wire DTO.

## Technical debt
- `detail` is nominally non-null but `GlobalErrorHandler.FallbackExceptionHandler` can pass `ex.getMessage()` (guarded) — the record does not enforce non-nullness on `title`/`detail`. Low risk.

## Duplicate logic
None within batch. Conceptually parallel to `JilaliEnvelope` (both serde records) but they model different contracts (outbound problem+json vs. inbound upstream envelope) — not duplication.

## Dead or unused code
None. Framework-invoked via `@Serdeable`; `of` is used by `GlobalErrorHandler`.

## Refactoring recommendations
- None urgent. Optionally add a factory overload that accepts `HttpStatus` directly to remove the `.getCode()` bridging at every call site in `GlobalErrorHandler`.
