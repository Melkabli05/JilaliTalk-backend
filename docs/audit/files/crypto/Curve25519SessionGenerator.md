# Curve25519SessionGenerator

`src/main/java/com/jilali/crypto/Curve25519SessionGenerator.java`

## Purpose
Generates ephemeral Curve25519 (X25519) session keys for HelloTalk's `ht/encbin` encryption: creates a fresh key pair, derives the ECDH shared secret against the server's public key, and returns the `x-ht-pub` header value plus the shared secret hex. Mirrors `userinfo/generatorkey.js`.

## Responsibilities
- Generate an X25519 key pair via BouncyCastle.
- Compute the shared secret by calling `generateSecret()` directly on the private key params (avoids the `X25519Agreement` path that is broken on GraalVM Native Image).
- Build the `x-ht-pub` header (`serverPub || myPub` hex) and return both as a record.

## Public API
- `static Curve25519Session generate(String serverPublicKeyHex)` — throws `RuntimeException` on any failure.
- `record Curve25519Session(String headerValue, String sharedSecret)` — both `String`, non-null on success.
- `final` class, private constructor.

## Dependencies
- BouncyCastle: `X25519KeyPairGenerator`, `X25519PrivateKeyParameters`, `X25519PublicKeyParameters`, `Hex`.
- `SecureRandom`.
- Depended on by: `HelloTalkAuthClientImpl`, `TranslateUpstreamHeaders`, `TranslateService`, `JilaliGateway`.

## Coupling and cohesion analysis
High cohesion — one job (ECDH session generation). Coupling to BC crypto is inherent and appropriate. Stateless, thread-safe. Uses `SecureRandom` for key generation (line 28). Clean.

## Code smells
- **No input validation** on `serverPublicKeyHex`: `Hex.decode` (line 36) will throw on malformed hex and `new X25519PublicKeyParameters(bytes, 0)` requires exactly 32 bytes — a wrong-length key surfaces as a generic wrapped `RuntimeException` rather than a clear "invalid server public key" message.
- **Broad catch(Exception)** (line 45) collapses all failures into one opaque `RuntimeException("Curve25519 key generation failed")`.

## Technical debt
- No validation that the server pub key is 32 bytes before use; a config typo produces a confusing error.

## Duplicate logic
None within batch. The record `Curve25519Session` is unique to this file.

## Dead or unused code
- Both record accessors (`headerValue`, `sharedSecret`) are consumed by translate/auth/gateway callers (grep-confirmed). None dead.

## Refactoring recommendations
- Validate `serverPublicKeyHex` length (== 64 hex chars / 32 bytes) up front with a specific error message.
- Narrow the catch or at least include the offending context (key length) in the thrown message.
