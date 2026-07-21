# JilaliErrorResponseFilter

`src/main/java/com/jilali/core/JilaliErrorResponseFilter.java`

## Purpose
A `@ClientFilter(serviceId = "jlhub")` response filter that logs the raw upstream response body for any failed (status ≥ 400) upstream call. Runs last (`getOrder() == Integer.MAX_VALUE`) so it observes the final response.

## Responsibilities
- On each upstream response with status ≥ 400, read the body as a String and log method/path/status/body at WARN.

## Public API
- `@ResponseFilter void onError(HttpRequest<?> request, HttpResponse<?> response)` — framework-invoked.
- `int getOrder()` — `Integer.MAX_VALUE` (run last).

## Dependencies
- SLF4J logging only.
- Depended on by: framework-instantiated; no direct callers.

## Coupling and cohesion analysis
Very high cohesion — pure diagnostic logging. Zero domain coupling. This is an observability concern, not part of the error-translation path.

## Code smells
- **Response body consumption risk**: `response.getBody(String.class)` inside a client filter can consume/buffer a streamed body that `UpstreamTransportExceptionHandler` also reads (it calls `ex.getResponse().getBody(String.class)`). Both reading the same upstream error body is a potential double-read / interaction hazard depending on Micronaut buffering. Worth verifying no body is lost to the exception handler because this filter already drained it.

## Technical debt
- Logs full upstream body at WARN unconditionally on any 4xx/5xx — could be noisy and may log sensitive upstream data. Consider truncation or debug-level for large bodies.

## Duplicate logic
- Overlaps in intent with `GlobalErrorHandler.UpstreamTransportExceptionHandler`, which also logs the upstream body (there for 502 mapping). Two places log the same failure at two layers. Not a correctness bug but redundant logging; consolidate if noise becomes an issue.

## Dead or unused code
None. `onError` is framework-invoked via `@ResponseFilter`.

## Refactoring recommendations
- Confirm this filter's body read does not starve `UpstreamTransportExceptionHandler`'s read (buffering); if it does, log from a single place.
- Truncate logged bodies and/or gate on debug level.
