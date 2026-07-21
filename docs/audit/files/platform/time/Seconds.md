## Purpose

Single helper for the seconds-to-milliseconds conversion that's repeated across the upstream mappers (HelloTalk's wire shape carries Unix seconds; the BFF and the Angular frontend work in milliseconds). Centralizes the magic number and gives every site one place to evolve.

## Public API

- `public final class Seconds` (static utility)
- `public static long toMillis(long unixSeconds)` — multiplies by 1000; `0` stays `0`
- `public static long nowMillis()` — single call site for "current epoch milliseconds"

## Dependencies

- **Inbound**: `comment/dto/Comment.java` (`fromWireSeconds`), `realtime/HtNotifyMapper.java` (5 sites), `realtime/HtCcNotifyMapper.java` (3 sites).
- **Outbound**: `java.time.Instant` (only for `nowMillis`).

## Coupling and cohesion

Single responsibility, minimal surface, no state. Lives in `com.jilali.platform.time` (a new sub-package, the first time-related helper).

## Code smells

None — 2-method utility.

## Technical debt

None.

## Duplicate logic

This is the deduplicated form. Before Refactor 7, the `* 1000L` multiplication appeared in 10 sites across 3 files.

## Dead or unused code

None — both methods are used.

## Java 25 modernization opportunities

- Could become a static-import-friendly facade (the call sites already use static imports via `java.util.Optional`, so this style is consistent).
- `toMillis` could be `Math.multiplyExact(unixSeconds, 1000L)` for overflow detection — but the existing per-site behavior (silent wrap on overflow) is the long-standing contract, and changing it would be a behavior change.

## Micronaut built-in opportunities

None — this is a plain utility class. A Java 25 enhanced switch on a sealed `TimePrecision` enum (Seconds, Millis, Nanos) is a possible future evolution but not warranted today.

## Refactoring recommendations

None needed — this is the canonical, minimal shape for the seconds-to-ms helper.
