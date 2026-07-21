# DeviceIdStore

`src/main/java/com/jilali/core/DeviceIdStore.java`

## Purpose
A static utility that loads or creates a persistent 32-char hex device id (MD5-UUID shaped), stored at `./data/device_id`, so the BFF looks like a consistent returning device to the upstream anti-fraud stack across restarts.

## Responsibilities
- Read the persisted device id if present and non-blank.
- Otherwise generate a random-seeded, MD5-UUID-derived 32-char hex id, persist it, and return it.
- Degrade gracefully to an in-memory generated id if the data dir is unreadable/unwritable.

## Public API
- `static String loadOrCreate()` — returns the persisted-or-freshly-created device id.
- (private) `static String generate()` — 16 random bytes → `UUID.nameUUIDFromBytes` → hex, dashes stripped.
- Class is `final` with a private constructor (non-instantiable).

## Dependencies
- Uses: `java.nio.file.Files/Path`, `java.security.SecureRandom`, `java.util.UUID`.
- Depended on by: `JilaliProperties` (line 41, `deviceId` fallback).

## Coupling and cohesion analysis
High cohesion, minimal coupling — a self-contained persistence helper called from exactly one place. Clean.

## Code smells
- **Hardcoded path** `./data/device_id` (line 29) — relative to CWD, not configurable. Minor **Primitive Obsession** (string path constant).
- Catch-all `catch (IOException)` silently degrades (line 48) without logging — a persistence failure is invisible to operators.

## Technical debt
- Relative `./data` path is fragile across different working directories / container layouts; should be configurable or derived from a data-dir property.
- No logging on the degrade path — a device id that silently fails to persist reintroduces the exact "new device every restart" failure the class exists to prevent, with no signal.

## Duplicate logic
None within batch.

## Dead or unused code
None. `loadOrCreate()` invoked from `JilaliProperties`.

## Refactoring recommendations
- Make the storage path configurable (property or env), defaulting to a well-defined data dir.
- Log at WARN when falling back to a non-persisted id.
