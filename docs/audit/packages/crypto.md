# `com.jilali.crypto` — reverse-engineered HelloTalk ciphers

## Purpose

Reimplements HelloTalk's proprietary/legacy ciphers purely for **wire-protocol interoperability** with HelloTalk's private mobile API. These are not cryptosystems jilalibff chose for its own security — they exist only because the upstream server expects clients to speak them.

## File responsibilities (8 files)

| File | One-line summary |
|---|---|
| `ApkSignatureGenerator.java` | Generates the `android_apk_signature` header value upstream requires on every WS login (mimics HelloTalk's own APK signing). |
| `Cc2018Cipher.java` | Custom CC2018 block cipher (HelloTalk-proprietary). |
| `Curve25519SessionGenerator.java` | Curve25519 ephemeral keypair + ECDH shared-secret — used by the translate/codec encrypted-field path. |
| `EncbinUtil.java` | AES-encrypts/decrypts the `ht/encbin` envelope HelloTalk returns for user-info and similar lookups. |
| `HtntKeyUtil.java` | Derives the HelloTalk-session HTNT key from session salt + constants. |
| `Md5Util.java` | MD5 helper (used for sign computation + email-password hashing). |
| `QqTeaCipher.java` | QQ-TEA cipher (legacy Tencent). |
| `TeaCipher.java` | Original XTEA/TEA cipher used as a primitive by `QqTeaCipher`. |

## Dependencies

- **Inbound**: imported by `core` (filters), `auth` (login), `client` (envelope unwrapping), `im` (binary frame encryption), `translate` (encrypted-field codec), `user` (encrypted profile lookups). Heavy consumer.
- **Outbound**: BouncyCastle (`bcprov-jdk18on`) is on the classpath but used only by `Curve25519`/`EncbinUtil`; everything else is pure-JDK.
- No dependencies on any feature package.

## Improvement opportunities

1. **High (security)**: **MD5** (`Md5Util`) is used for password hashing in `auth/HelloTalkAuthClientImpl`. This matches HelloTalk's own protocol (so it's required for interop), but a target rewrite should isolate this clearly so it never gets reused for a BFF-internal crypto decision.
2. **Medium**: every cipher file should publish its concrete "HelloTalk wire-format compatibility" doc comment (which smali class/cmdId it implements, which JSON field it goes into) — currently the package is opaque to anyone who hasn't done the smali reverse-engineering.
3. **Medium**: target-architecture split: `com.jilali.platform.crypto.interop` (this directory) vs. any BFF-internal crypto (currently zero — there is none). Clear naming reduces the risk of someone "improving" MD5 hashing later.
4. **Low**: any class that uses `SecureRandom` (search for it) should be tagged as thread-safe explicitly — ciphers instantiated once per process and reused concurrently need this guarantee.
