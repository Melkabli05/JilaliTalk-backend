# Md5Util

`src/main/java/com/jilali/crypto/Md5Util.java`

## Purpose
MD5 utilities for HelloTalk's login/signature schemes: plain MD5 hex, the double-MD5 email-login password hash, the `/v3/login` `sign` field, and the visitor-history `sign` field.

## Responsibilities
- `md5Hex`: lowercase hex MD5 of a UTF-8 string.
- `emailPasswordHash`: `MD5(MD5(password + cnonce) + nonce)`.
- `loginSignature`: `MD5("client_version=..&deviceid=..&login_type=..&ts=.." + LOGIN_SIGN_SECRET)`.
- `visitorHistorySign`: `MD5(jid + jid + clientTs)`.

## Public API
- `static String md5Hex(String input)`
- `static String emailPasswordHash(String password, String cnonce, String nonce)`
- `static String loginSignature(String clientVersion, String deviceId, int loginType, long timestampMs)`
- `static String visitorHistorySign(long jid, long clientTs)`
- `private static final String LOGIN_SIGN_SECRET = "Q5bw4aJ9Pp16MYxsErWSYaxKzn4wy2ed"` (line 58).
- `final` class, private constructor.

## Dependencies
- JDK: `MessageDigest`, `StandardCharsets`.
- Depended on by: `HtntKeyUtil`, `ProfileController`, `HelloTalkAuthClientImpl`, `EmailPreLoginResponse`.

## Coupling and cohesion analysis
High cohesion around HelloTalk's MD5-based signing. Low coupling (JDK only). Stateless/thread-safe (`MessageDigest.getInstance` per call — correct, since `MessageDigest` is not thread-safe if shared). Appropriate central home for these schemes.

## Code smells
- **Hardcoded secret** `LOGIN_SIGN_SECRET` (line 58) — a recovered HelloTalk constant; expected for impersonation, well-documented provenance.
- **MD5 usage**: cryptographically broken hash, but mandated by the upstream protocol (not our security boundary) — flagged for record, not actionable.
- **Own hex encoder** `bytesToHex` with `String.format("%02x", b)` per byte (lines 110–116) — slow and inconsistent with `ApkSignatureGenerator`'s `HexFormat`.
- Broad `catch (Exception)` wrapping (line 19) for an algorithm that is always present ("MD5" is guaranteed by the JDK) — `NoSuchAlgorithmException` can never fire here.

## Technical debt
- Hex encoding style diverges from the rest of the crypto package (see package doc).
- `emailPasswordHash` takes a plaintext password parameter — unavoidable for the scheme, but note it flows a plaintext credential through this utility.

## Duplicate logic
- `bytesToHex` (lines 110–116) duplicates hex-encoding present in `ApkSignatureGenerator` (`HexFormat`) and `EncbinUtil` (`hexStringToBytes` inverse) — three hex implementations in one package.
- `loginSignature` / `visitorHistorySign` / `HtntKeyUtil.compute` share the `MD5(fields + secret)` family pattern (see HtntKeyUtil.md).

## Dead or unused code
- All four public methods are referenced (grep-confirmed via `ProfileController`, `HelloTalkAuthClientImpl`, `EmailPreLoginResponse`, `HtntKeyUtil`). None dead.

## Refactoring recommendations
- Replace `bytesToHex` with `HexFormat.of().formatHex(...)` and standardize hex across the crypto package.
- Drop the impossible `catch` or narrow to `NoSuchAlgorithmException` with an assertion comment.
