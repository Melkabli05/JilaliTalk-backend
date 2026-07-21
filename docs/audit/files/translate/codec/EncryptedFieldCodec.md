# EncryptedFieldCodec

`src/main/java/com/jilali/translate/codec/EncryptedFieldCodec.java`

## Purpose
Encodes translator field payloads with AES under a per-request Curve25519-derived shared secret, then Base64 for JSON transport. This translator-specific field envelope is distinct from HelloTalk’s whole-body `ht/encbin` wire format, although it reuses `EncbinUtil`’s raw-byte primitive.

## Public API
- `byte[] encrypt(byte[] plaintext, String sharedSecretHex)` — AES-encrypts raw bytes.
- `byte[] decrypt(byte[] ciphertext, String sharedSecretHex)` — decrypts raw ciphertext.
- `String encryptAndBase64(String plaintext, String sharedSecretHex)` — UTF-8 encodes, encrypts, and Base64 encodes text.
- `String decryptBase64(String base64Ciphertext, String sharedSecretHex)` — reverses Base64, AES, and UTF-8 encoding.

## Coupling
A stateless Micronaut singleton used by `TranslateService`; delegates cryptography to `EncbinUtil` and uses JDK Base64/UTF-8.

## Notes
- The delegated construction is unauthenticated AES-ECB with PKCS7 (`EncbinUtil.java:109-113`): ECB leaks repeated-block patterns and provides no integrity. It is an upstream compatibility constraint, not a safe general-purpose design.
- No secret is hardcoded here. The translator static **public** key has a configurable but hardcoded default at `src/main/resources/application.yml:135`.
- `EncryptedFieldCodec.java:24-40` validates neither nulls, Base64, ciphertext length, nor key shape; `EncbinUtil.java:121-128` also assumes even-length valid hex. Failures propagate as low-level runtime exceptions.
