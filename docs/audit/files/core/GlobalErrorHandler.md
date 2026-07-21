# GlobalErrorHandler

`src/main/java/com/jilali/core/GlobalErrorHandler.java`

## Purpose
A container class holding three Micronaut `ExceptionHandler` singletons that translate exceptions into RFC 9457 `application/problem+json` responses with correct HTTP status, so the frontend never sees an upstream `code` buried inside a 200 body or a raw 500.

## Responsibilities
- `JilaliExceptionHandler`: maps `JilaliException` → `ApiError` at `ex.status()`, preserving `upstreamCode`.
- `UpstreamTransportExceptionHandler`: maps `HttpClientResponseException` → 502 Bad Gateway, logging the upstream body.
- `FallbackExceptionHandler`: maps any `Exception` → 500, preventing raw stack traces from leaking.

## Public API
- `GlobalErrorHandler` (outer) — `final`, no instances used; namespace only.
- `static final class JilaliExceptionHandler implements ExceptionHandler<JilaliException, HttpResponse<ApiError>>` — `handle(HttpRequest, JilaliException)`.
- `static final class UpstreamTransportExceptionHandler implements ExceptionHandler<HttpClientResponseException, HttpResponse<ApiError>>` — `handle(...)`.
- `static final class FallbackExceptionHandler implements ExceptionHandler<Exception, HttpResponse<ApiError>>` — `handle(...)`.
- Each is `@Singleton @Produces("application/problem+json") @Requires(classes = {...})`.

## Dependencies
- Builds: `ApiError.of(...)`.
- Handles: `JilaliException`, `HttpClientResponseException`, `Exception`.
- Micronaut: `ExceptionHandler` SPI, `HttpStatus`, `HttpResponse`, `@Produces`, `@Requires`.
- Depended on by: framework-dispatched. `JilaliException` (its handled type) is thrown from `RoomController`, `RoomJoinService`, auth/translate/client packages.

## Coupling and cohesion analysis
Good cohesion per handler (one exception type each — the Javadoc correctly notes this avoids a god-handler). The outer class is purely an organizational namespace. Coupling is healthy: only to `ApiError` and the exception types.

## Code smells
- Mild **Shotgun Surgery risk**: the `PROBLEM_JSON` constant, `MediaType.of(PROBLEM_JSON)`, and the `ApiError.of(...).status(...)` construction pattern are repeated in all three handlers (lines 42–49, 71–79, 92–99). A shared helper would remove the triplication.
- **Primitive Obsession**: `ex.status().getCode()` bridging `HttpStatus`→`int` (line 43) recurs (see ApiError doc's suggested `HttpStatus` factory overload).

## Technical debt
- The three near-identical response-building blocks are copy-paste; a private `problem(HttpStatus, title, detail, upstreamCode)` helper on the outer class would centralize the shape.
- `FallbackExceptionHandler` on `Exception` is very broad and will also swallow framework exceptions that might deserve their own status; acceptable as a safety net but worth monitoring logs.

## Duplicate logic
- Internal triplication of the problem-response construction (see Code smells).
- Conceptually related to `JilaliErrorResponseFilter` (which only logs upstream error bodies) — not duplicate, complementary. See "one error story" note below.

## Dead or unused code
None. All three `handle` methods are framework-invoked via the `ExceptionHandler` SPI (`@Requires(classes = ExceptionHandler.class)`).

## Refactoring recommendations
- Extract a shared `private static HttpResponse<ApiError> problem(HttpStatus status, String title, String detail, Integer upstreamCode)` to remove the triplication.
- Add an `ApiError.of(HttpStatus, ...)` overload to drop the `.getCode()` bridging.

## Error-handling story note
Error handling is largely coherent and single-sourced here: `JilaliException` (thrown across the app) is the canonical failure type, `GlobalErrorHandler` is the single translation point, and `ApiError` is the single wire shape. `JilaliErrorResponseFilter` is a **logging-only** client-side observer, not a competing mechanism. The one seam: `UpstreamTransportExceptionHandler` and `JilaliErrorResponseFilter` both read/log the upstream error body — mild redundancy but different layers (server exception vs. client filter).
