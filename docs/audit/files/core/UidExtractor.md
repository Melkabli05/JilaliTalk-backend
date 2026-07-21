# UidExtractor

`src/main/java/com/jilali/core/UidExtractor.java`

## Purpose
A static utility that extracts the `uid` claim from a JWT (raw, not `Bearer`-prefixed) using Jackson, returning it as a String or long, null-safe with a `0`/`"0"` default on any failure.

## Responsibilities
- Split the JWT, pad and Base64url-decode the payload, `ObjectMapper.readTree(...).path("uid")`, return as text or long.
- Log a WARN on null/blank/malformed input.

## Public API
- `static String uidAsString(String jwt, ObjectMapper om)` — uid text, `"0"` on failure.
- `static long uidAsLong(String jwt, ObjectMapper om)` — uid long, `0L` on failure.
- (private) `uidAsStringImpl`, `uidAsLongImpl` — near-identical implementations.
- `final` class, private constructor.

## Dependencies
- Jackson `ObjectMapper` (passed in), SLF4J.
- Depended on by: `ProfileController`, `DefaultHeadersClientFilter` (line 167), `RoomEventSource`, `ImEventSource`, `ImSendController`.

## Coupling and cohesion analysis
High cohesion (one job) but internal cohesion is weakened by two parallel implementations differing only in return type. Coupling is fine (takes `ObjectMapper` as a param rather than injecting). Overlaps with `JwtUtil` — see Duplicate logic.

## Code smells
- **Duplicate methods**: `uidAsStringImpl` (lines 26–49) and `uidAsLongImpl` (lines 51–74) are byte-for-byte identical except the final `.asText("0")` vs `.asLong(0L)` and the log/return defaults. Classic copy-paste; one should delegate to the other.
- Thin pass-through public methods (`uidAsString` → `uidAsStringImpl`) add an indirection layer with no value (line 17–24).
- **Inconsistent failure contract vs `JwtUtil`**: returns `0`/`"0"` (a valid-looking uid!) on failure, whereas `JwtUtil` returns `null`. A `0` uid can silently propagate into headers as a real-looking value.

## Technical debt
- Failure sentinel `0`/`"0"` masks parse errors as a plausible uid — a `null`/`OptionalLong` would be safer at call sites that build headers.
- Requires callers to thread an `ObjectMapper` through; a package-private shared mapper (as `EncbinUtil` uses) would simplify call sites.

## Duplicate logic
- **Duplicate of `JwtUtil`** (same package): both decode a JWT payload to read `uid`. `DefaultHeadersClientFilter` uses both. See JwtUtil.md for the consolidation recommendation.
- Internal duplication between the two `*Impl` methods.

## Dead or unused code
- `uidAsString` (String variant) — grep-confirmed used by `ProfileController`/event sources, so not dead. Both public methods are used.

## Refactoring recommendations
- Collapse `uidAsLongImpl`/`uidAsStringImpl` into one decode returning the `uid` `JsonNode`, with thin typed accessors on top.
- Merge with `JwtUtil` into a single JWT-claim utility with a consistent null-based failure contract and optional `Bearer` handling.
