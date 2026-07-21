# HelloTalkAuthClientImpl

`src/main/java/com/jilali/auth/HelloTalkAuthClientImpl.java`

## Purpose
The concrete implementation of `HelloTalkAuthClient`: hand-built HTTP calls against HelloTalk's `/user_register_center/**` (and `/user_info_updater/**`) auth microservice. It handles the two wire codecs (`bin/cc2018` and `ht/encbin`), the device-fingerprint personas (Android for most calls, iOS for `/v3/login`), envelope unwrapping, password/signature hashing, and Curve25519 session encryption for signup.

## Responsibilities (this class does a LOT — see cohesion)
- Serialize request DTOs to JSON with a null-omitting `ObjectMapper` (Gson-compatibility requirement).
- Encode/decode `bin/cc2018` bodies (`cc2018Exchange`, `readEnvelope`).
- Encrypt/decrypt `ht/encbin` bodies via Curve25519 session key (`encbinPost`, `encbinExchange`, `signupCheck`).
- Unwrap the `HelloTalkEnvelope` `{status,msg,data}` shape and redact JWTs in logs.
- Build two distinct header personas (`androidHeaders`, `iosLoginHeaders`).
- Orchestrate the two-step pre_login → login exchange, including MD5 password/signature computation.
- Hold ~20 hardcoded persona constants (OS, versions, device models, net type, behavior-validate placeholder).
- Map upstream rejections to `Optional.empty()` and transport failures to `JilaliException`.

## Public API
- `HelloTalkAuthClientImpl(@Client("jlhub") HttpClient, JilaliProperties)` — constructor injection.
- `Optional<LoginResponse> login(String, String)` — pre_login then performLogin.
- `void regPrepare()` — best-effort `reg/prepare`; swallows `RuntimeException`.
- `void sendEmailCode(String)` — `send_email_code` via encbin.
- `void checkNickname(String)` — `reg/profile_check` via encbin.
- `Optional<SignCheckResponse> signupCheck(String, String, String)` — `/v3/check` via encbin with Curve25519 + verify_token filtering.
- Private helpers: `preLogin`, `performLogin`, `cc2018Exchange`, `readEnvelope`, `encbinPost`, `encbinExchange`, `androidHeaders`, `iosLoginHeaders`.

## Dependencies
- Injects: `@Client("jlhub") HttpClient`, `JilaliProperties` (deviceId, deviceModel, serverPubKeyHex).
- Crypto: `ApkSignatureGenerator`, `Cc2018Cipher`, `Curve25519SessionGenerator`, `EncbinUtil`, `HtntKeyUtil`, `Md5Util`.
- Core: `JilaliException`; Jackson `ObjectMapper`.
- All upstream DTOs in `dto/upstream`.
- Depended on BY: injected as the `HelloTalkAuthClient` bean into `HelloTalkAuthService`, `im/ImEventSource`, `im/HtImUpstreamConnector`.

## Coupling and cohesion analysis
This is the lowest-cohesion class in the package and the primary refactor target. It mixes at least five distinct concerns: (1) JSON serialization policy, (2) cc2018 codec plumbing, (3) encbin/Curve25519 encryption plumbing, (4) two device-persona header builders, and (5) auth-flow orchestration (pre_login/login/signup sequencing + crypto hashing). It is tightly coupled to six concrete crypto utility classes (all static-method utilities, hard to substitute/test). It correctly depends on the `HttpClient` and `JilaliProperties` abstractions, but the crypto layer is all concrete statics.

## Code smells
- **God Class (borderline) / Low Cohesion**: 341 lines mixing codec, crypto, header personas, and orchestration.
- **Long Method**: `performLogin` (lines 136–171) builds a 20+ argument `EmailLoginRequest` inline with positional args — extremely error-prone (Long Parameter List at the call site).
- **Primitive Obsession / Magic Constants**: ~20 hardcoded persona constants (lines 64–106); the iOS `behavior_validate` placeholder (line 106) is a security-relevant magic string.
- **Duplicated header-builder structure**: `androidHeaders` and `iosLoginHeaders` are near-identical shapes (see Duplicate logic).
- **Duplicated exchange structure**: `cc2018Exchange` and `encbinExchange` share the same try/retrieve/catch skeleton.
- **Feature Envy hint**: the request-construction logic knows intimate details of every upstream DTO's field order.

## Technical debt
- Hardcoded personas and versions (`6.3.40`, `iPhone13,2`, build `93`, client_version_num `394024`) will silently rot when HelloTalk bumps its app version and starts rejecting stale client versions. No config/override.
- `BEHAVIOR_VALIDATE_PLACEHOLDER = "jilalibff-no-sdk-available"` (line 106) is a deliberate anti-fraud bypass that only works while upstream treats the field as a presence check — brittle and could break or get the account flagged at any time.
- The `readEnvelope` catch logs the entire raw response as text on decode failure (lines 264–269) — could leak sensitive upstream payloads into logs (JWT redaction only happens on the success path, line 259–262).
- No tests around the crypto/wire assembly, which is the most fragile code in the app.

## Duplicate logic
- `androidHeaders` (296–317) and `iosLoginHeaders` (326–340) duplicate the header-setting pattern with different constants — a persona abstraction would remove the duplication.
- `cc2018Exchange` (216–238) and `encbinExchange` (279–294) share the same POST/retrieve/`HttpClientResponseException`→empty skeleton.
- The envelope-unwrap (`readEnvelope`) is cc2018-specific; the encbin path decrypts directly to the DTO (`signupCheck`, line 210) — two different response-decode paths for what is conceptually the same "unwrap upstream response" step. Compare with `HelloTalkEnvelope` usage; envelope handling is centralized here (good) but only for the cc2018 codec.

## Dead or unused code
- None dead. All private helpers are reachable from the public methods. Framework-managed bean.

## Refactoring recommendations
- Extract the two codec plumbings into dedicated collaborators, e.g. `Cc2018Transport` and `EncbinTransport`, each exposing `exchange(path, dto, headers) -> Optional<byte[]>` and owning the try/catch skeleton — removes the duplicated exchange code and shrinks this class to orchestration.
- Replace the ~20 persona constants with a `DevicePersona`/config-bound value object (Android vs iOS), and make `androidHeaders`/`iosLoginHeaders` one method parameterized by persona.
- Replace the positional `EmailLoginRequest` construction in `performLogin` with a builder or a static factory (mirroring `SignCheckRequest.forEmailSignup`) to eliminate the 20-arg call.
- Move `behavior_validate` and app-version constants into configuration so they can be updated without a code change.
- Ensure the decode-failure log path (line 264) also redacts JWTs before dumping raw bytes.
