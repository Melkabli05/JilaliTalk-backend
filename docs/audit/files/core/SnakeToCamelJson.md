# SnakeToCamelJson

`src/main/java/com/jilali/core/SnakeToCamelJson.java`

## Purpose
A static utility that recursively renames JSON object keys from snake_case to camelCase, preserving array structure and leading underscores. A port of the frontend's retired `snake-to-camel.adapter.ts`, moved to the BFF so the conversion happens once at the wire boundary.

## Responsibilities
- Recursively walk a `JsonNode` tree (objects/arrays), rebuild it with camelCased object keys.
- `toCamel`: convert a single key, preserving leading underscores (`_id` stays `_id`).

## Public API
- `static JsonNode convert(JsonNode node)` — returns a new tree with camelCased keys; returns the node unchanged for scalars/null.
- `static String toCamel(String key)` — package-private; the key transform.
- `final` class, private constructor.

## Dependencies
- Jackson: `JsonNode`, `ArrayNode`, `ObjectNode`, `JsonNodeFactory`.
- Depended on by: `CamelCaseResponseFilter` (sole caller).

## Coupling and cohesion analysis
High cohesion, single responsibility. Coupling only to Jackson tree types. Pairs cleanly with its one caller.

## Code smells
- None significant. `toCamel` is a tight manual char loop (lines 48–61) — slightly more complex than a regex but avoids regex overhead per key; acceptable and well-commented.

## Technical debt
- Rebuilds the entire tree via `JsonNodeFactory.instance` on every response body (allocations proportional to payload size). Fine at current scale; noted as the cost paid per response by `CamelCaseResponseFilter`.

## Duplicate logic
None within batch. This is the single canonical case-conversion (the point of moving it out of the frontend).

## Dead or unused code
None. `convert` used by `CamelCaseResponseFilter`; `toCamel` used internally and is package-private (likely for unit tests).

## Refactoring recommendations
- None needed. Optionally memoize `toCamel` results for hot keys if profiling ever shows key-conversion cost, but almost certainly unnecessary.
