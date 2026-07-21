# JwtUtil

`src/main/java/com/jilali/core/JwtUtil.java`

## Purpose
A static utility that extracts the `uid` claim from a `Bearer <jwt>` string **without signature verification** — the gateway is trusted to have already validated the token; this only reads a non-sensitive claim to add per-request context to outbound headers.

## Responsibilities
- Strip the `Bearer ` prefix, split the JWT, Base64url-decode the payload, regex-match `"uid": <digits>`, return it as `Long` or `null`.

## Public API
- `static Long uidFromBearer(String bearerToken)` — returns the uid claim or `null` on any parse failure.
- `final` class, private constructor.

## Dependencies
- JDK only: `Base64`, `Pattern`, `StandardCharsets`.
- Depended on by: `DefaultHeadersClientFilter` (line 134), `TranslateService`, `JilaliGateway`.

## Coupling and cohesion analysis
High cohesion, zero external coupling. Clean single-purpose utility. However it **overlaps functionally with `UidExtractor`** (same package), which also decodes a JWT payload to read `uid` — see Duplicate logic.

## Code smells
- **Regex-parsing JSON**: `"\"uid\"\\s*:\\s*(\\d+)"` (line 19) parses JSON with a regex rather than a JSON reader — brittle if the payload formatting varies (e.g. `uid` as a quoted string, or a substring collision). `UidExtractor` does the same job with a real `ObjectMapper`, making this the weaker of the two implementations.

## Technical debt
- No signature verification is a deliberate documented choice, appropriate for a BFF behind a trusted gateway — flagged only for awareness, not as a defect.
- Regex approach diverges from `UidExtractor`'s Jackson approach; two subtly different parsers for the same claim is a latent inconsistency (one returns `null`, the other `0`/`"0"` on failure).

## Duplicate logic
- **Duplicate of `UidExtractor`**: both extract `uid` from a JWT payload. `JwtUtil.uidFromBearer` (regex, returns `Long`/null, handles the `Bearer ` prefix) vs `UidExtractor.uidAsLong/uidAsString` (Jackson, returns primitive/`"0"` default, no Bearer stripping). `DefaultHeadersClientFilter` uses **both** (lines 134 and 167) — a concrete symptom of the redundancy.

## Dead or unused code
None. Used by three files.

## Refactoring recommendations
- Consolidate JWT-uid extraction into one utility. Prefer `UidExtractor`'s Jackson-based decode for robustness, add `Bearer`-prefix handling and a nullable variant, and delete the regex path. This removes the double-extractor use inside `DefaultHeadersClientFilter`.
