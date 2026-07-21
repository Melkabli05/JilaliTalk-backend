# HtntKeyUtil

`src/main/java/com/jilali/crypto/HtntKeyUtil.java`

## Purpose
Computes the `htntkey` field for HelloTalk's signup-flow request body: `MD5(deviceId + loginType + timestampMs + "abccdfef#*")`, reproducing native `TeaUtils.getMd5WithKey(s, 3)`.

## Responsibilities
- Concatenate `deviceId + loginType + timestampMs` with the fixed `KEY` suffix and MD5-hex it (via `Md5Util`).

## Public API
- `static String compute(String deviceId, int loginType, long timestampMs)` — lowercase MD5 hex.
- `final` class, private constructor; `private static final String KEY = "abccdfef#*"`.

## Dependencies
- Delegates to `Md5Util.md5Hex`.
- Depended on by: `HelloTalkAuthClientImpl` (sole caller).

## Coupling and cohesion analysis
Very high cohesion — one derivation, one line of logic. Correctly reuses `Md5Util` rather than reimplementing MD5. Stateless/thread-safe. Clean.

## Code smells
- **Hardcoded key** `"abccdfef#*"` (line 38) — a HelloTalk protocol constant, expected for this impersonation tool.
- **No input validation** on `deviceId` (null would produce `"null..."` in the hash), but callers control the input; low risk.

## Technical debt
- The class doc notes `htntkey` is currently used only by the signup `/v3/check` flow and exists for parity/future-proofing — so it is thin but intentionally kept.

## Duplicate logic
- Conceptually parallel to `Md5Util.loginSignature` / `visitorHistorySign` (all are "concatenate fields + secret, MD5-hex"). The three share the same `MD5(fields + secret)` pattern but with different field layouts and secrets — related family, not literal duplication. `HtntKeyUtil` correctly lives separately since its secret/layout differ.

## Dead or unused code
- `compute` is used by `HelloTalkAuthClientImpl` (grep-confirmed). Not dead despite the "parity/future-proofing" note.

## Refactoring recommendations
- Optionally consolidate the `MD5(fields + secret)` signature family (`HtntKeyUtil.compute`, `Md5Util.loginSignature`, `Md5Util.visitorHistorySign`) under a shared internal helper, keeping the distinct secrets/layouts as parameters — only if it improves clarity; the current split is defensible.
