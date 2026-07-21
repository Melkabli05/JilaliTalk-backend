# Cc2018Cipher

`src/main/java/com/jilali/crypto/Cc2018Cipher.java`

## Purpose
Implements HelloTalk's `bin/cc2018` HTTP body codec: `payload = random_16_byte_key || TEA_ciphertext`. A fresh random key is generated per message and prepended; encryption is delegated to `TeaCipher`.

## Responsibilities
- `encode`: generate a 16-byte `SecureRandom` key, TEA-encrypt the plaintext, prepend the key.
- `decode`: split the key prefix from the ciphertext, TEA-decrypt.

## Public API
- `static byte[] encode(byte[] plaintext)` — throws `IllegalArgumentException` on empty (via `TeaCipher`).
- `static byte[] decode(byte[] payload)` — throws `IllegalArgumentException` if payload `< KEY_LEN + 8`.
- `final` class, private constructor; `KEY_LEN = TeaCipher.KEY_LEN`.

## Dependencies
- Delegates to `TeaCipher.encrypt/decrypt`.
- Uses `SecureRandom`.
- Depended on by: `UserController`, `RoomUserProfileResponse`, `HelloTalkAuthClientImpl`, `JilaliClient`, `JilaliGateway`.

## Coupling and cohesion analysis
High cohesion — just the cc2018 framing over `TeaCipher`. Correctly delegates the block cipher rather than reimplementing it (the extraction to `TeaCipher` was done for this reason). Static `SecureRandom RANDOM` (line 31) is thread-safe. Clean.

## Code smells
- **Security-by-design caveat (documented)**: the per-message key ships in the same message it decrypts (line 18–21), so cc2018 adds no confidentiality beyond TLS — it only obscures the body. This is inherent to the upstream protocol, not a defect in this port; correctly documented.
- Input validation present (`decode` length check, line 48). Good.

## Technical debt
- `KEY_LEN + 8` minimum in `decode` (line 48) is a magic threshold (key + one TEA block); relies on `TeaCipher` internals. Slight coupling to the block size constant, tolerable.

## Duplicate logic
- **Block-transform duplication with `QqTeaCipher`**: `Cc2018Cipher` delegates to `TeaCipher`, but `TeaCipher` and `QqTeaCipher` contain byte-identical `DELTA`, `readKeyWords`, `encryptBlock`/`decryptBlock`, and CBC-like chaining (see TeaCipher.md / QqTeaCipher.md). `Cc2018Cipher` itself is not duplicated.

## Dead or unused code
None. `encode`/`decode` used across user/auth/client packages.

## Refactoring recommendations
- None for this class. The relevant dedup is between `TeaCipher` and `QqTeaCipher` (see those docs).
