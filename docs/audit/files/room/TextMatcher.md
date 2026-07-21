# TextMatcher.java

`src/main/java/com/jilali/room/TextMatcher.java`

## Purpose
Java port of the frontend's `createSearchMatcher` (text-search.util.ts): case- and accent-insensitive substring matching, tokenized on whitespace. Kept as static pure functions (no bean) for trivial unit-testability.

## Responsibilities
- Normalize text: NFD-decompose, strip combining diacritics, lowercase, trim.
- Match: every whitespace-separated query token must be a substring of at least one normalized haystack; blank query matches everything.

## Public API
- `static String normalize(String value)` — NFD + strip `\p{M}` + lowercase + trim; null → `""`.
- `static boolean matches(List<String> haystacks, String query)` — token-AND substring match, accent/case-insensitive.
- Constant `COMBINING_DIACRITICS = Pattern.compile("\\p{M}")` (compiled once).
- Final class, private constructor.

## Dependencies
- JDK only: `java.text.Normalizer`, `java.util.regex.Pattern`, streams.
- Depended on BY: `RoomsSearchService.matchesQuery` (line 80) only.

## Coupling and cohesion analysis
Excellent: high cohesion (pure text-matching), zero framework coupling, no injected state. Textbook extract-and-test-in-isolation utility.

## Code smells
- **Minor inefficiency**: `matches` re-normalizes all haystacks (line 39-42) on every call; for the search service this runs per `ChannelListItem`. Acceptable at page-of-20 scale.
- No ReDoS risk: the only regex is the fixed `\p{M}` character-class (no user input compiled into a pattern, no catastrophic backtracking construct). `split("\\s+")` is likewise linear. **Safe against ReDoS.**
- Does not reimplement anything the JDK does better — `String.contains` + `Normalizer` is the idiomatic approach. No algorithmic concern.

## Technical debt
- Behaviour is intentionally coupled to the frontend `text-search.util.ts`; the two must be kept in sync manually (no shared spec). Note for maintainers.

## Duplicate logic
- Conceptually duplicates the frontend `normalizeForSearch`/`createSearchMatcher` (documented, deliberate cross-stack port). No duplication within the Java batch.

## Dead or unused code
None. `normalize` is used internally and is a legitimate public helper; `matches` is called by `RoomsSearchService`.

## Refactoring recommendations
- If search volume grows, pre-normalize haystacks once per item rather than per query token loop (already loops tokens outer, haystacks inner — fine).
- Consider documenting the JS source hash/version to make the sync contract explicit.
- No security or performance action needed.

## Cross-reference
See `RoomsSearchService.md`.
