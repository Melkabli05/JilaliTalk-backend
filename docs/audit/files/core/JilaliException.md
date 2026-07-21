# JilaliException

`src/main/java/com/jilali/core/JilaliException.java`

## Purpose
The canonical application `RuntimeException` for upstream failures. Carries the original upstream `code` and a target `HttpStatus` so `GlobalErrorHandler` can render a correct RFC 9457 problem detail. Provides static factories mapping known upstream codes to statuses.

## Responsibilities
- Carry `upstreamCode` + `HttpStatus` alongside a message.
- `fromCode(int, String)`: declarative mapping of known upstream error codes → HTTP statuses (default 502).
- `upstreamFailure(String, Throwable)`: wrap non-structured failures (I/O, parse) as 502.

## Public API
- `JilaliException(int upstreamCode, String message, HttpStatus status)` — full constructor.
- `JilaliException(String message, HttpStatus status)` — convenience (upstreamCode 0).
- `int upstreamCode()`.
- `HttpStatus status()`.
- `static JilaliException fromCode(int code, String msg)` — code→status switch.
- `static JilaliException upstreamFailure(String stage, Throwable cause)` — 502 wrapper.

## Dependencies
- Micronaut: `HttpStatus`.
- Handled by: `GlobalErrorHandler.JilaliExceptionHandler`.
- Thrown/used by: `RoomController`, `HelloTalkAuthClient`, `LoginOutcome`, `HelloTalkAuthClientImpl`, `TranslateClient`, `HtTranslateClient`, `TranslateService`, `JilaliResponses`, `JilaliGateway`.

## Coupling and cohesion analysis
High cohesion — one exception type with focused factories. Broadly used across packages but that is expected for a canonical error type; coupling is to `HttpStatus` only. This is the backbone of the single error-handling story.

## Code smells
- **Magic numbers**: the `fromCode` switch (lines 39–54) maps bare integer codes (100002, 190041, …) with only comments to explain them. Borderline **Primitive Obsession**; an enum of known upstream codes would be more maintainable, but the switch is genuinely declarative and well-commented.
- Two-arg constructor defaults `upstreamCode` to `0`, which collides with the "success" sentinel used elsewhere (`JilaliEnvelope.isSuccess()` treats 0 as success). Using 0 to mean "no upstream code" on an exception is a mild semantic overload.

## Technical debt
- The code→status table will grow (Shotgun Surgery pressure: every new upstream code discovered requires editing this one switch — acceptable since it is intentionally the single mapping point).

## Duplicate logic
None within batch.

## Dead or unused code
- Both factories and both constructors are referenced across the codebase (grep-confirmed usages in auth/translate/room/client packages). None dead.

## Refactoring recommendations
- Consider a named constant/enum for the known upstream codes to document them beyond inline comments.
- Consider a distinct sentinel (e.g. `-1` or `OptionalInt`) for "no upstream code" to avoid overloading `0`.
