# CamelCaseResponseFilter

`src/main/java/com/jilali/core/CamelCaseResponseFilter.java`

## Purpose
A Micronaut `@ServerFilter` that rewrites every outbound JSON response body from the upstream's snake_case wire format to camelCase, so the Angular frontend receives idiomatic JSON with no client-side adapter.

## Responsibilities
- Intercept all server responses (`MATCH_ALL_PATTERN`).
- Skip non-JSON, null, `byte[]`, and already-`JsonNode` bodies.
- Convert the response body to a `JsonNode` tree and delegate key renaming to `SnakeToCamelJson.convert`.

## Public API
- `CamelCaseResponseFilter(ObjectMapper om)` — constructor injection of Jackson mapper.
- `@ResponseFilter void camelCase(MutableHttpResponse<?> response)` — framework-invoked response filter method.

## Dependencies
- Injects: `com.fasterxml.jackson.databind.ObjectMapper`.
- Delegates to: `SnakeToCamelJson`.
- Micronaut HTTP: `@ServerFilter`, `@ResponseFilter`, `MutableHttpResponse`, `MediaType`.
- Depended on by: nothing directly (framework-instantiated). Only consumer of `SnakeToCamelJson`.

## Coupling and cohesion analysis
High cohesion — one job (case-convert responses). Low coupling — depends only on `ObjectMapper` and `SnakeToCamelJson`. This is a **server** filter and is the only case-conversion filter; no overlap with the three `@ClientFilter` classes (which act on outbound upstream requests, a different direction).

## Code smells
- Minor: `isJson` defaults to `true` when content type is unset (line 48), relying on Micronaut's "unset defaults to application/json on send" behavior — a subtle implicit contract that could silently mangle a future non-JSON body served without a content type. Documented in a comment, so acceptable.

## Technical debt
- Re-serializes every JSON body to a tree and back (`om.valueToTree` + convert) on every response — a per-response allocation/CPU cost. Fine at current scale; note it if response throughput becomes a concern.

## Duplicate logic
None. Conversion algorithm lives entirely in `SnakeToCamelJson`.

## Dead or unused code
None. `camelCase` is framework-invoked via `@ResponseFilter`.

## Refactoring recommendations
- Consider narrowing the `@ServerFilter` pattern to `/api/**` (the Javadoc says it targets `/api/**` but the annotation uses `MATCH_ALL_PATTERN`) to avoid touching WebSocket/actuator/other responses. This is a real doc-vs-code mismatch worth resolving.
