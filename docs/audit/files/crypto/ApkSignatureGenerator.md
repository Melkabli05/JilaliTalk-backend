# ApkSignatureGenerator

`src/main/java/com/jilali/crypto/ApkSignatureGenerator.java`

## Purpose
Generates the `android_apk_signature` field required by HelloTalk's `ht_im/sock` login packet, plus exposes the impersonated build's `VERSION_NAME`/`VERSION_CODE`. Reproduces the native `TeaUtils.xConnInfo` HMAC-SHA256 over a fixed data layout.

## Responsibilities
- HMAC-SHA256 (`HmacSHA256`, key used as raw UTF-8 bytes) over `sig1 + tsSec + VI_VALUE + sig2 + deviceId`.
- Append `tsSec` to the hex HMAC to form the final signature.
- Provide `VERSION_NAME` / `VERSION_CODE` constants for consistent `client_version` across callers.

## Public API
- `static String generate(String deviceId, long timestampMs)` — the signature.
- `public static final String VERSION_NAME = "6.3.70"`.
- `public static final int VERSION_CODE = 11276`.
- `final` class, private constructor.

## Dependencies
- JDK crypto: `Mac`, `SecretKeySpec`, `HexFormat`.
- Depended on by: `DefaultHeadersClientFilter` (uses `VERSION_NAME`), `ProfileController`, `HelloTalkAuthClientImpl`, `Md5Util` (Javadoc ref), `HtImUpstreamConnector`.

## Coupling and cohesion analysis
High cohesion — one signature scheme + shared version constants. Low coupling (JDK crypto only). Stateless, thread-safe (no shared mutable state; `Mac` created per call). Appropriate.

## Code smells
- **Hardcoded secrets/constants**: `HMAC_KEY` (line 29), the captured X.509 cert blob `APK_SIG` (line 34), `VI_VALUE` (line 43). Expected for this impersonation tool (these ARE HelloTalk's constants, not our secrets), but note the sig blob + HMAC key are embedded verbatim.
- **Magic numbers**: `499` / `998` substring split (lines 56–57) — documented against the native `substring` split; acceptable.

## Technical debt
- `VI_VALUE`, `VERSION_NAME`, `VERSION_CODE` must be kept in lockstep with the impersonated build; three separate constants encode the same build identity and can drift. Consider deriving `VI_VALUE` from the version constants.

## Duplicate logic
- `hmacHex` (lines 62–71) is a local HMAC-SHA256-to-hex helper; the app also has `StringUtils.hmacSha256` conceptually (native side) but no Java duplicate in this batch. `HexFormat.of().formatHex` here vs manual `%02x` loops in `Md5Util` — inconsistent hex-encoding style across crypto package (see crypto package doc).

## Dead or unused code
None. `generate` and both version constants are referenced (grep-confirmed across auth/im/user/core).

## Refactoring recommendations
- Derive `VI_VALUE` from `VERSION_NAME`/`VERSION_CODE` (+ channel) to eliminate drift between the three build-identity constants.
- Standardize hex encoding across the crypto package on `HexFormat`.
