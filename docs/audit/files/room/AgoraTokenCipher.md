# AgoraTokenCipher.java

`src/main/java/com/jilali/room/AgoraTokenCipher.java`

## Purpose
Utility that converts the AES-encrypted `rtc_info.token` LiveHub returns into the plain Agora RTC token the browser SDK requires (the plain token carries the App ID after a `006`/`007` version prefix). Without this the gateway reports `CAN_NOT_GET_GATEWAY_SERVER: invalid vendor key, can not find appid`.

## Responsibilities
- Detect already-plain tokens (`006`/`007` prefix) and pass them through untouched.
- Hex-decode the encrypted blob and AES-128/ECB/NoPadding decrypt it.
- Strip trailing NUL padding bytes left by upstream zero-padding.
- Fail soft: on any error return the original token rather than dropping it.

## Public API
- `static String decrypt(String token, byte[] key)` — decrypts a hex-encoded AES token; returns input unchanged if null/empty, already-prefixed, or undecryptable.
- (private) `static byte[] hexToBytes(String hex)` — hex string to bytes, throws `IllegalArgumentException` on odd length / non-hex.
- Final class, private constructor — non-instantiable static holder.

## Dependencies
- Imports: `javax.crypto.Cipher`, `javax.crypto.spec.SecretKeySpec`, `java.nio.charset.StandardCharsets`. No framework, no injection.
- Depended on BY: `RoomController.decryptRtcToken` (line 237), `RoomJoinService.decryptRtcToken` (line 192), `JilaliGateway.publisherToken` (line 300). Key supplied from `JilaliProperties.agoraCipherKey()`.

## Coupling and cohesion analysis
High cohesion — one well-scoped cryptographic transformation. Low coupling: pure static function taking primitives, no beans. Good design boundary; the one blemish is that three callers each independently fetch the key and call it (see Duplicate logic).

## Code smells
- **Weak crypto mode**: `AES/ECB/NoPadding` (line 36). ECB is a discouraged mode (leaks block patterns). Acceptable only because it must match the legacy HelloTalk web client's scheme — but flag it.
- **Swallow-all catch** (line 45): `catch (Exception _)` returns the raw token silently. Masks genuine key/format misconfiguration; a decrypt failure and a pass-through look identical.
- Minor **Primitive Obsession**: key passed as raw `byte[]` rather than a typed key holder.

## Technical debt
- No validation that `key` length is a legal AES size (16/24/32); a misconfigured key throws inside and is swallowed, silently degrading to pass-through of an encrypted token.
- The fail-soft behaviour means a wrong key produces no error, only a broken room at the browser. Consider logging at debug.

## Duplicate logic
The decrypt-token-from-properties wrapper (`decryptRtcToken`) is duplicated in `RoomController` and `RoomJoinService` — see those files. This class itself is not duplicated.

## Dead or unused code
None. All three call sites are live.

## Refactoring recommendations
- Centralise key retrieval + decrypt into a single injectable `AgoraTokenService` (or keep static but add one `decryptRtcToken(VoiceRoomInfoResponse)` helper reused by controller and service) to remove the 3-way duplication.
- Add explicit key-length validation and a debug log on decrypt failure.
- Document the ECB constraint as a deliberate compatibility decision in a SECURITY note.

## Security notes
- The AES key defaults to the hardcoded value `15helloTCJTALK20` (`application.yml:141`, env-overridable via `LIVEHUB_AGORA_CIPHER_KEY`). A committed default secret is a leak risk even when overridable.
- ECB mode (line 36) is cryptographically weak but constrained by upstream compatibility.
