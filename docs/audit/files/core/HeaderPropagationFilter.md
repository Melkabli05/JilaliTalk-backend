# HeaderPropagationFilter

`src/main/java/com/jilali/core/HeaderPropagationFilter.java`

## Purpose
A `@ClientFilter(serviceId = "jlhub")` that copies the inbound caller's configured headers (auth, `x-ht-*` device context, `x-b3-*` tracing) from the current server request onto every outbound upstream request, so the BFF carries the frontend's identity/context downstream.

## Responsibilities
- For each header in `properties.forwardedHeaders()`, if present on the inbound request, copy its value to the downstream request.

## Public API
- `HeaderPropagationFilter(JilaliProperties properties)` — constructor.
- `@RequestFilter void propagate(MutableHttpRequest<?> downstream)` — framework-invoked.

## Dependencies
- Injects: `JilaliProperties` (reads `forwardedHeaders()`).
- Uses: `ServerRequestContext.currentRequest()`.
- Depended on by: framework-instantiated; referenced in Javadoc by `DefaultHeadersClientFilter`, `SessionAuthClientFilter`, `JilaliClient`.

## Coupling and cohesion analysis
Very high cohesion — one job (propagate inbound headers). Minimal coupling (only `JilaliProperties`). Notably does **not** implement `Ordered`, so it runs at Micronaut's default order (0) — earlier than `SessionAuthClientFilter` (100) and `DefaultHeadersClientFilter` (MAX_VALUE). This ordering is intentional (real frontend headers win first) but only implicitly enforced by the absence of an order; see below.

## Code smells
- **Implicit ordering**: relies on default order 0 to run before the other two client filters. Correct today but fragile — not self-documenting that this must run first. (The other two filters document their relationship to it, but this class does not declare its own precedence.)

## Technical debt
- The filter's precedence is load-bearing (auth/session logic in the other filters assumes frontend headers are already applied) yet expressed only by omission. An explicit `getOrder()` returning a low constant would make the contract enforceable and reviewable.

## Duplicate logic
- Shares the "iterate `forwardedHeaders()` and copy per header" pattern with `DefaultHeadersClientFilter.addDefaults` (lines 73–80), where the same loop supplies defaults for absent headers. The two filters form a propagate-then-default pair; the loop structure is parallel but not identical (copy inbound vs. default when missing).

## Dead or unused code
None. `propagate` is framework-invoked.

## Refactoring recommendations
- Add an explicit `implements Ordered` with a small constant (e.g. 0 or a named `PROPAGATION_ORDER`) so the intended "runs first" contract is enforced and documented alongside the other filters' orders.
