# QqTeaCipher

`src/main/java/com/jilali/crypto/QqTeaCipher.java`

## Purpose
QQTEA — Tencent QQ's TEA variant with CBC-like chaining — used by HelloTalk's `ht_im/sock` binary protocol to encrypt push-notification payloads once a session key is negotiated. Ported field-for-field from the reference client's `QQTEA` JS object.

## Responsibilities
- `encrypt`: build a random-padded header (`headerLen` from `(8-(len+10)%8)%8+3`) + 7-byte trailer, then encrypt via CBC-like chaining.
- `decrypt`: chain-decrypt, then strip the header derived from `out[0] & 0x07`.

## Public API
- `static byte[] decrypt(byte[] buffer, byte[] key)` — returns null if `buffer` null or `< 8`.
- `static byte[] encrypt(byte[] buffer, byte[] key)` — returns null if buffer null; `key` must be 16 bytes.
- `final` class, private constructor; `DELTA`, static `SecureRandom RANDOM`.

## Dependencies
- JDK: `ByteBuffer`, `ByteOrder`, `SecureRandom`.
- Depended on by: `HtImFrameDecoder` (and referenced by `Cc2018Cipher` Javadoc as the sibling variant).

## Coupling and cohesion analysis
High cohesion — one cipher variant. No app coupling. Static `RANDOM` is thread-safe. **But** it is largely a copy of `TeaCipher`'s core (see Duplicate logic) — the two share DELTA, key-word reading, block transforms, and chaining nearly verbatim.

## Code smells
- **Inconsistent error contract**: returns `null` on bad input (lines 27, 33) whereas the sibling `TeaCipher` throws `IllegalArgumentException`. Two TEA variants in the same package with opposite failure styles.
- **No key-length validation**: `encrypt` documents "key 16 bytes" but does not enforce it; `readKeyWords` (line 47) reads fixed offsets 0/4/8/12 and would `IndexOutOfBounds` (or silently misread) on a wrong-size key — `TeaCipher.requireKey` guards this, `QqTeaCipher` does not.
- **Magic numbers**: header formula constants `10`, `3`, trailer `7`, mask `0x07` inline (lines 34–36) — `TeaCipher` at least names some (`MIN_HEADER_LEN`, `TRAILER_LEN`).
- **Missing trailer integrity check**: `TeaCipher.decrypt` verifies the 7-byte zero trailer; `QqTeaCipher.decrypt` does not, so a wrong key yields garbage silently.

## Technical debt
- Divergence from `TeaCipher` (validation, trailer check, error contract) despite implementing the same cipher family means fixes must be applied twice — Shotgun Surgery risk across the two cipher files.

## Duplicate logic
- **Near-total duplication with `TeaCipher`**: `DELTA` (line 21 vs TeaCipher 23), `readKeyWords` (46–49 vs 87–90), `decryptBlock` (51–63 vs 92–104), `encryptBlock` (65–77 vs 106–118), `decryptCore`/`encryptCore` chaining (79–151 vs `decryptChain`/`encryptChain` 121–184) are byte-for-byte the same algorithm. Only the padding/header framing and the public method contracts differ. This is the single largest duplication in the crypto package.

## Dead or unused code
- **`encrypt` is unused in production** (grep-confirmed): `src/main/java` calls only `QqTeaCipher.decrypt` (4 sites in `HtImFrameDecoder` — the BFF only reads inbound push payloads, never encrypts them). `encrypt` is referenced solely by `HtImFrameDecoderTest` (6 sites) to synthesize test vectors. So it is a test-only method — keep it (it backs the round-trip tests) but note it has no production caller.
- `decrypt` is live (`HtImFrameDecoder`).

## Refactoring recommendations
- Extract the shared TEA core (`DELTA`, `readKeyWords`, `encryptBlock`, `decryptBlock`, and the chaining) into a common private base/helper used by both `TeaCipher` and `QqTeaCipher`, leaving only the differing padding/framing and public contracts per class.
- Add key-length validation and (if the protocol has one) a trailer/integrity check to `QqTeaCipher`, and align its error contract with `TeaCipher`.
- If `encrypt` is confirmed unused, remove it.
