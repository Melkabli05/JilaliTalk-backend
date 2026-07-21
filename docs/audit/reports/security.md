# Security Observations

> File:line precision. Each finding is sourced from the per-package or per-file audit. Closure priority is in the technical-debt report.

## 1. Plaintext JWT at rest in `auth_session` (CRITICAL — HIGH)

- **Where**: `auth/JdbcAuthSessionRepository.java` line 45 — `INSERT INTO auth_session(..., jwt, ...)`. Schema in `auth/AuthSchemaInitializer.java` (the column `jwt VARCHAR(4000)` is literally plaintext).
- **What**: Each session row stores the upstream HelloTalk JWT verbatim — the credential this BFF uses to call HelloTalk's private API.
- **Impact**: filesystem read = live upstream credential disclosure. Anyone with file-read or DB-dump access can mint new requests as the configured HelloTalk account.
- **Fix**: encrypt the column with server-side AES-GCM (key in env/config, not code); or migrate session storage to an external KMS-backed secret manager. Add `expired_at` + a scheduled reaper.
- **Status**: NOT a Phase-rewrite-only concern — fix immediately in-place before any further work.

## 2. `manager/ManagerController` has no authorization checks (CRITICAL — HIGH)

- **Where**: `manager/ManagerController.java` (full file).
- **What**: No `@Secured`, no `@RolesAllowed`, no comparison of inbound JWT `uid` against `host_id`, no moderator-list lookup. The `set`, `approve`, `judge`, and `list` endpoints trust every field of the request body verbatim.
- **Impact**: any authenticated caller can promote/demote moderators or approve moderator operations on any room.
- **Fix**: either (a) add Micronaut `@Secured` + a `@RequiresOwnedByRoom` custom annotation, or (b) document explicitly that an upstream trusted gateway enforces this (and verify that documentation is accurate — currently NOT in place).
- **Status**: Fix in-place before feature work continues.

## 3. `vip/VipExperienceCardController` authorization gap (CRITICAL — HIGH)

- **Where**: `vip/VipExperienceCardController.java` (per-package audit confirmed).
- **What**: no `@Secured`/principal binding; `userId` taken from query/body on every endpoint except `claimTrial` (which delegates identity to `JilaliGateway`). No idempotency token on `use`/`receiveFriendCard`.
- **Impact**: any authenticated caller can read another user's VIP card state, and client retries can double-claim.
- **Fix**: same as #2 + add `@Idempotent` (or `X-Idempotency-Key` header per upstream convention).
- **Status**: Fix in-place.

## 4. `stage/StageController` authorization likely absent (CRITICAL — HIGH, verify)

- **Where**: `stage/StageController.java` (per-package audit's "same class as `manager`" note).
- **What**: same `(cname, userId)` request pattern; stage operations (kick, mute, raise-hand) need moderator authorization that the controller doesn't enforce.
- **Fix**: same as #2.
- **Status**: Verify exact behavior with focused review, then fix in-place.

## 5. `im/ImSendController` outbound-side authorization (MEDIUM, verify)

- **Where**: `im/ImSendController.java`.
- **What**: `read`, `typing`, `send` accept any `userId` path variable; backend-owns-identity model means there's no per-caller check — but if the BFF ever becomes multi-tenant, this becomes a real problem.
- **Fix**: at minimum, document the assumption. If multi-tenancy is planned, add an inbound `Authorization`-JWT-vs-`userId` check.

## 6. `auth/HelloTalkAuthService` MD5 password hashing (LOW — protocol constraint)

- **Where**: `auth/HelloTalkAuthService.java` — `Md5Util.emailPasswordHash(...)`.
- **What**: MD5 of MD5 is used to match what HelloTalk's upstream auth microservice expects.
- **Impact**: this is `IMPERSONAL` from a security standpoint — needed for protocol interop, NOT something you'd use for BFF-internal password storage. The danger is if someone re-uses this for a BFF-internal credential store.
- **Fix**: keep `crypto.Md5Util` clearly labeled as "wire-interop only"; document the warning in the class's Javadoc.

## 7. `core/AuthTokenHolder` thread-safety (LOW — already correct)

- **Where**: `core/AuthTokenHolder.java`.
- **What**: `AtomicReference<String>` — thread-safe by construction.
- **Action**: none — confirmed safe.

## 8. `crypto/*.java` cipher thread-safety (LOW — verify)

- **Where**: `crypto/QqTeaCipher.java`, `crypto/TeaCipher.java`, `crypto/Cc2018Cipher.java`, `crypto/EncbinUtil.java`.
- **What**: if any cipher holds mutable state (e.g. an instance-level `key` field), it must be safe to reuse across concurrent calls.
- **Fix**: verify with a single grep for `this.field = ...` or similar; if any cipher is not thread-safe, document explicitly or wrap in `ThreadLocal`/synchronization.

## 9. SQL-injection risk in `auth/JdbcAuthSessionRepository` (LOW — already mitigated)

- **Where**: `auth/JdbcAuthSessionRepository.java` (per auth-package audit).
- **What**: all three statements use `PreparedStatement` with bound `?` parameters.
- **Action**: none — already safe.

## 10. Session-id randomness in `auth/JdbcAuthSessionRepository` (LOW — already correct)

- **Where**: `auth/JdbcAuthSessionRepository.java` line 25 — static `SecureRandom`.
- **What**: 32-byte ids → 256-bit hex (`randomId`). Strong randomness.
- **Action**: none.

## 11. HTTP-header XHT-Session / X-HT-Session not implemented (LOW — known gap)

- **Where**: backend-level — the original HelloTalk X-HT-Session header (an xTEA-encrypted session blob) is documented in `re_output` but not implemented here.
- **Impact**: if the upstream ever starts rejecting requests without it (a real possibility per smali), the BFF could break silently.
- **Fix**: monitor and add if needed; the encryption key lives in `Lcom/hellotalk/utils/TeaUtils;->getKey` (native) which would need a Frida hook or shared-library reverse-engineering pass to extract.

## 12. Inbound request validation gaps (MEDIUM)

- **Where**: most `@Body`-annotated controller parameters — coverage of `@Valid` is inconsistent (per the comment audit's finding: e.g. `SendCommentRequest` only validates `cname`, while `BffSendCommentRequest` validates three fields).
- **Impact**: malformed inputs reach the service layer unvalidated.
- **Fix**: add `@Valid` to every `@Body`-annotated controller method; add `@NotNull`/`@NotBlank`/`@Min`/`@Positive` to every request DTO field where the value is required.

## 13. `core/CamelCaseResponseFilter` overrides `@JsonProperty` (LOW — cosmetic)

- **Where**: `core/CamelCaseResponseFilter.java` (per-comment audit).
- **What**: filter renames response keys from snake_case to camelCase, making per-field `@JsonProperty` annotations effectively dead-letter.
- **Impact**: future contributors will add `@JsonProperty` thinking they are setting the wire name, when in fact the filter overrides them.
- **Fix**: drop the `@JsonProperty` annotations on outbound-bound DTOs OR drop the filter (one or the other, not both).

---

## Summary (security findings)

| # | Severity | Fix window |
|---|---|---|
| 1. Plaintext JWT at rest | CRITICAL | Before further work |
| 2. `manager` auth gap | CRITICAL | Before further work |
| 3. `vip` auth gap | CRITICAL | Before further work |
| 4. `stage` auth gap (suspected) | CRITICAL (verify) | Before further work |
| 12. Inbound validation gaps | MEDIUM | During rewrite |
| 5, 7, 8, 9, 10, 11, 13 | LOW | As touched during rewrite |

The four P0 items all share a class: **missing authorization boundary on internal-BFF REST endpoints**. Closing them is not a rewrite concern — they're independent defects to fix in-place, ideally before doing the architectural rewrite so the new structure encodes secure-by-default behavior from the start.
