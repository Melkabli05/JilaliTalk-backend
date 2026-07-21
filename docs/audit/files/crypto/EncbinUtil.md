# EncbinUtil

`src/main/java/com/jilali/crypto/EncbinUtil.java`

## Purpose
Encrypts/decrypts HelloTalk's `ht/encbin` payloads using **AES-256-ECB with PKCS7 padding** via BouncyCastle's low-level API (chosen to avoid JCA provider routing issues on GraalVM Native Image). Handles JSON (de)serialization and optional gzip on decrypt.

## Responsibilities
- `encrypt(Object, key)`: JSON-serialize then AES-encrypt.
- `encryptRaw(byte[], key)` / `decryptRaw(byte[], key)`: raw-bytes crypt with no JSON step.
- `decrypt(byte[], key, Class<T>)`: AES-decrypt then JSON-deserialize.
- `decryptToJson(...)`: AES-decrypt then optional gunzip.
- Hex→bytes conversion; gzip sniffing.

## Public API
- `static byte[] encrypt(Object payload, String sharedSecretHex)`
- `static <T> T decrypt(byte[] encrypted, String sharedSecretHex, Class<T> clazz)`
- `static byte[] encryptRaw(byte[] plaintext, String sharedSecretHex)`
- `static byte[] decryptRaw(byte[] encrypted, String sharedSecretHex)`
- `static byte[] decryptToJson(byte[] encrypted, String sharedSecretHex)`
- `final` class, private constructor; private static shared `ObjectMapper MAPPER`.

## Dependencies
- BouncyCastle: `AESEngine`, `PKCS7Padding`, `PaddedBufferedBlockCipher`, `KeyParameter`.
- Jackson `ObjectMapper` (static, configured).
- JDK: `GZIPInputStream`.
- Depended on by: `HelloTalkAuthClientImpl`, `EncryptedFieldCodec`, `JilaliGateway`.

## Coupling and cohesion analysis
High cohesion around the `ht/encbin` codec, though the class mixes three concerns: block crypto, JSON (de)serialization, and gzip handling. Coupling appropriate. `AESEngine`/cipher instance is created per `crypt` call (line 109) so there is no shared mutable cipher state — thread-safe; the static `MAPPER` is thread-safe by Jackson contract.

## Code smells
- **Insecure mode — AES-ECB** (line 20 doc, `AESEngine` with no mode/IV, lines 109–112): ECB leaks plaintext structure (identical blocks → identical ciphertext). This mirrors HelloTalk's own scheme so it is dictated by the upstream protocol, not a free choice — but it IS an insecure mode and must be flagged. There is no IV anywhere in the class.
- **No key-length validation**: `hexStringToBytes` (lines 121–130) will happily produce a non-16/24/32-byte key; `crypt` then wraps the BC failure in a generic message. A malformed `sharedSecretHex` yields an opaque error.
- **Repeated crypt boilerplate**: `encrypt`, `encryptRaw`, `decryptRaw`, `decryptToJson` each duplicate the `output = new byte[len+32]; crypt(...); copyOf` sequence (lines 33–37, 63–67, 79–83, 95–98).
- **Fragile output sizing**: `plaintext.length + 32` slack for padding (lines 33, 64, 80, 95) is a magic constant.
- **Broad multi-catch ladders** re-wrapping exceptions with slightly different messages in every method.

## Technical debt
- `hexStringToBytes` assumes even-length, valid hex with no checks (`hex.charAt(i+1)` will `IndexOutOfBounds` on odd length).
- ECB is a documented protocol constraint but should carry a prominent security note; there is no authentication/MAC on the ciphertext.

## Duplicate logic
- The four `output buffer + crypt + copyOf` blocks are near-identical and should collapse into one private `cryptToExact(input, key, forEncryption)` returning the trimmed array. `decryptToJson` and `decryptRaw` differ only by the trailing `maybeGunzip`.
- `hexStringToBytes` duplicates hex-decoding also done elsewhere (`Hex.decode` in `Curve25519SessionGenerator`, `HexFormat` in `ApkSignatureGenerator`) — three different hex approaches across the crypto package.

## Dead or unused code
- All five public methods are referenced by auth/translate/gateway callers (grep-confirmed). None dead.

## Refactoring recommendations
- Extract a single `private static byte[] cryptExact(byte[] in, byte[] key, boolean encrypt)` to remove the four duplicated buffer dances.
- Validate hex/key length with a clear error.
- Standardize hex decoding across the crypto package on `HexFormat`.
- Add a prominent doc note that ECB + no MAC is an upstream-protocol constraint, not a security property.
