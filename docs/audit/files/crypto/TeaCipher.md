# TeaCipher

`src/main/java/com/jilali/crypto/TeaCipher.java`

## Purpose
The reduced-round (16-round) TEA cipher behind native `TeaUtils.xTEADecrypt/xTEAEncrypt`, with non-standard CBC-like plaintext/ciphertext-feedback chaining and a custom padding scheme (variable random header + 7-byte all-zero integrity trailer). Takes the 16-byte key explicitly; framing is decided by callers (`Cc2018Cipher`, `HtImPayloadCipher`).

## Responsibilities
- `encrypt`: validate, build padded buffer (random header + data + zero trailer), chain-encrypt.
- `decrypt`: validate block alignment, chain-decrypt, verify the zero trailer (integrity/key check), strip header, return data.
- Block transforms + CBC-like chaining core.

## Public API
- `static byte[] encrypt(byte[] plaintext, byte[] key)` — throws `IllegalArgumentException` on empty plaintext or wrong key length.
- `static byte[] decrypt(byte[] ciphertext, byte[] key)` — throws `IllegalArgumentException` on non-8-multiple length; `IllegalStateException` on trailer-check / length-underflow failure.
- `public static final int KEY_LEN = 16`.
- `final` class, private constructor; `DELTA`, `MIN_HEADER_LEN=3`, `TRAILER_LEN=7`, static `SecureRandom RANDOM`.

## Dependencies
- JDK: `ByteBuffer`, `ByteOrder`, `SecureRandom`.
- Depended on by: `Cc2018Cipher` (and `HtImPayloadCipher` per Javadoc).

## Coupling and cohesion analysis
High cohesion — a clean, well-validated block cipher primitive. No app coupling. Static `RANDOM` is thread-safe; all state is local per call. This is the better-engineered of the two TEA implementations (validation, named constants, trailer integrity check). It was intentionally extracted from `Cc2018Cipher` for reuse.

## Code smells
- **Magic constant** `0xE3779B90` in `decryptBlock` (line 93) — commented as `delta*16 mod 2^32`; acceptable.
- Otherwise clean: input validation (`requireKey`, length checks), named padding constants, integrity trailer check.

## Technical debt
- Minimal. The reduced-round count and CBC-like scheme are dictated by the upstream native code, not a design choice here.

## Duplicate logic
- **Near-total duplication with `QqTeaCipher`**: `DELTA`, `readKeyWords`, `encryptBlock`, `decryptBlock`, and the chaining loops in `encryptChain`/`decryptChain` (lines 87–184) are byte-identical to `QqTeaCipher`'s equivalents. The two differ only in padding/framing and public error contract. See QqTeaCipher.md for the full mapping. This is the crypto package's largest duplication.

## Dead or unused code
- `encrypt` and `decrypt` both used via `Cc2018Cipher` (and IM payload cipher). `KEY_LEN` referenced by `Cc2018Cipher`. None dead.

## Refactoring recommendations
- Extract the shared TEA core into a common helper used by both `TeaCipher` and `QqTeaCipher` (this class is the higher-quality template — bring `QqTeaCipher` up to it: validation, named constants, trailer check, throwing contract).
