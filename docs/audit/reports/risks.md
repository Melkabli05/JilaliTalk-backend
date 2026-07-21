# Migration Risks & Mitigation

> What can break during the rewrite, and how to either prevent or detect it. Each risk is paired with a detection signal and (where possible) a mitigation that's cheaper than redoing the migration.

---

## R1. The upstream HelloTalk API is unofficial and may change

**Risk**: `jilalibff` is shaped to HelloTalk's undocumented private API. HelloTalk can (and historically does) change wire shapes, field names, encryption parameters, and signature algorithms without notice. A migration that assumes the API is stable sets the rewrite up for a hard surprise.

**Mitigation**:
- Every per-feature `infra/` package should isolate the wire call behind a port interface. The Angular frontend never depends on wire DTOs — only on the port mapping result. So a HelloTalk wire change ripples to ONE file per affected endpoint, not cross-cutting.
- Add a probe (`/health/upstream`) that checks the upstream is reachable and returns shape anomalies observed in recent responses.

**Detection**: per-feature integration test against the actual upstream (golden-response capture + drift detection); could even run as a CI job daily.

---

## R2. The Angular frontend is a separate repo and will break if DTO names/keys change

**Risk**: the rewrite collides DTOs (`Comment` + `CommentDto` → one record), but the Angular repo's TS types are hand-aligned. Forgetting a re-export or casing change can cause silent frontend breakage.

**Mitigation**:
- The Phase 4 DTO consolidations should each be shipped as TWO coordinated commits (BFF + frontend), not a sequence.
- Maintain a contract test where possible (the Angular frontend could pull from a TS schema generated off the Java DTOs; out of scope for this audit but worth flagging as a future improvement).

**Detection**: e2e smoke against `/api/*` for every affected endpoint after each DTO consolidation PR.

---

## R3. Reconnect state machines are fragile

**Risk**: `HtImUpstreamConnector` and `HtLiveHubUpstreamConnector` each manage connection state via `volatile` flags (`connected`, `intentionalClose`). Phase 3's consolidation to `@Retryable` + `@Scheduled` + `@PreDestroy` changes how shutdown and reconnect race. If tested by hand only, a regression could cause the BFF to silently stop processing upstream pushes after a restart.

**Mitigation**:
- Add a per-connector integration test that exercises the full lifecycle: connect → receive → disconnect (mid-message) → reconnect → receive again.
- Verify behavior matches the reference client (`old_hellotalk/scriptv2.js`) for at least the reconnect-after-disconnect case.
- Long-running soak test (≥ 1h continuous connect/reconnect cycles) before each Phase 3 milestone merge.

**Detection**: structured-log assertion that `WsConnectorState` transitions hit `Connected` after every `Reconnecting` window that comes from a known-warm upstream. CI fail = must investigate.

---

## R4. Authorization changes can lock out legitimate callers

**Risk**: adding `@Secured` to controllers that previously had no authorization could lock out the Angular frontend (which currently authenticates as the configured BFF identity). If the rewrite assumes multi-tenancy and the actual deployment is single-tenant, the lockout is silent — no users, no traffic.

**Mitigation**:
- Phase 1 must happen against a STAGING environment that mirrors production, with the feature-flagged new auth code path dual-running alongside the old.
- Define `JilaliAuthTier` in `auth/AuthSessionRepository` and use it to choose STRICT (one-user-per-request) vs LENIENT (shared BFF identity) enforcement per session.
- Have a kill-switch via env var (`JILALI_AUTH_STRICT=true|false`).

**Detection**: monitor unauthorized-response rate in metrics; alert at >0% would mean something misclassified was rejected.

---

## R5. Session JWT at rest has a backup history

**Risk**: encrypting the `jwt` column (T-1) requires a migration: rows written before the migration have plaintext JTW; rows written after have ciphertext. A naïve migration that drops the plaintext column on day one will lock out existing sessions and force every browser to re-login.

**Mitigation**:
- Phase 1.1 (encrypt-at-rest) must be a SHADOW migration: the new code reads BOTH the new encrypted column AND, as a fallback, the legacy plaintext column; writes only to the encrypted column. A scheduled task re-encrypts old rows over time. After all rows are migrated, drop the legacy column.
- The `AuthSession.expiresAt` column should be honored during migration: any legacy-plaintext row past its expiry is dropped, not migrated.

**Detection**: log-level assertion that every successful session lookup returned a freshly-migrated (encrypted) row; alert on any legacy-plaintext reads from app code.

---

## R6. Session replay attacks on cached translations

**Risk**: `@Cacheable("ai-translate")` keyed by `(text, targetLang)` could be cache-poisoned across users (one user's translation request, cached, served to another). Per `TranslateService`'s Javadoc this was deliberately addressed by also including the uid in the cache key — verifying that the comment is still accurate after the rewrite is essential.

**Mitigation**:
- After Phase 1, ensure no test exists that evades the user-keyed cache.
- Audit `TranslateService` cache-key construction in the rewrite — keep the documented discipline.

**Detection**: integration test verifying two different users submitting the same `(text, target_lang)` get separate cache entries.

---

## R7. Hardware — single-account JWT rotation

**Risk**: `AuthTokenHolder` holds the live upstream JWT for the single configured HelloTalk account. With current architecture, that JWT is refreshed on status-105 session mismatch via the auto-relogin flow (recently added). After Phase 2/3 refactoring, if any consumer accidentally captures the JWT at construction time (rather than reading it on each call), session refresh stops working.

**Mitigation**:
- The autotype of `AuthTokenHolder` uses `AtomicReference`. Lint rule: every consumer must access via `authTokenHolder.get()`, never via constructor-captured local. The audit already confirmed all 8 consumer files now read live.

**Detection**: a regression test where the test suite manually sets `authTokenHolder.set(newJwt)` and verifies every consumer sees the new value without restart.

---

## R8. JWT decode per-request regression

**Risk**: as noted in the performance report, the current code does 3 JWT decodes per request (`ProfileController.callerUserId()`, `ImSendController.callerUserId()`, `JilaliGateway.currentUserId()`). Performance report #4 calls for per-request uid caching. The risk is the opposite-direction regression: if the rewrite accidentally propagates the cached uid to a request that DOESN'T have an inbound JWT, the fallback behavior changes silently.

**Mitigation**:
- Per-request uid caching must be populated by an inbound `Authorization`-header-reading filter (or the `auth.SessionAuthClientFilter` chain), with the `JilaliProperties.defaultAuthToken()` fallback ONLY for the BFF's own requests.
- Test: a request WITHOUT `Authorization` header resolves to the BFF's own uid (current behavior). A request WITH a per-user `Authorization` resolves to that user's uid (also current).

**Detection**: integration test with both `Authorization` present and absent.

---

## R9. Cycle detection at compile-time vs runtime

**Risk**: `gradle compileJava` (or any IDE) doesn't immediately surface the circular `client ↔ feature` dependency because it discovers via annotation processing — many package-by-package imports only get resolved during actual compilation. A migration that thinks it has eliminated cycles might find them on first `gradle assemble`.

**Mitigation**:
- After Phase 2 is complete, add an architectural regression test that imports (transitively) every `*.class` file from `src/main/java/com/jilali/feature/` and asserts no `client`-side file appears in the transitive closure. This is testable in pure Java without any specific framework.
- Module-check plugin (`de.qaware.maven:prohibit-imports-of-bad-packages-maven-plugin` or Gradle equivalent) configured to fail the build if any feature package imports from another.

**Detection**: the architectural test fails the build.

---

## R10. DTO rename → Angular type changes

**Risk**: Phase 4's "consolidate DTOs" renames some fields (e.g. `UserInfo.X` → `User.Y` because the duplication merge uses one name). The Angular frontend's TS types (in `JilaliTalk-angular-frontend/src/app/features/user/...`) might have hand-typed keys that don't match.

**Mitigation**:
- The audit's `automation` recommendation: generate TS DTO types from the Java backend (e.g. via `typescript-generator` / `SchemaGenerator` + `OpenAPI 3.0`).
- Until that's in place, manual coordination via a single PR that includes both BFF and frontend changes.
- Re-test the most-used endpoints (chat send/receive, profile/me) end-to-end after every DTO consolidation.

**Detection**: end-to-end smoke test failures.

---

## R11. Filter execution order drift

**Risk**: Micronaut filter ordering is fragile — a new filter insertion or annotation change can reorder the chain. The current 4 `core/*Filter.java` files (camelCase, default-headers, error-response, header-propagation) must execute in the correct order. The audit's per-file docs note that `DefaultHeadersClientFilter` and `SessionAuthClientFilter` interact via priority — an accidental reorder changes the behavior of per-user vs. shared-account outbound calls.

**Mitigation**:
- After Phase 3 (where filters move from `core/` to `platform.filters/`), add a unit test that asserts the declared filter order.
- Add `@Order` explicitly to every filter in the target architecture (don't rely on implicit ordering).

**Detection**: the architectural-order test in the per-package test class.

---

## R12. Per-record injection-driven test debris

**Risk**: spec files reference `Session.class`, `LoginOutcome.class`, etc. in `src/test/`. Once Phase 2/3/4 shuffles package names, many test imports break. Default IDE refactoring catches most of this, but unique cases (cross-package imports) may need manual updates.

**Mitigation**:
- Each migration PR that touches public types should fail CI if test files don't compile (the gradle/Maven default already does this — DON'T skip the test step in any phase).
- For wholesale migrations, do a one-time package-by-package rename pass with both `src/main/java` and `src/test/java` updated in lockstep.

**Detection**: `gradle test` must pass before merging any phase PR.

---

## R13. New abstraction over-generalization

**Risk**: when extracting `UpstreamWebSocketConnector<TEvent>` during Phase 3, it's tempting to over-engineer (supports every conceivable wire format, every lifecycle option, every push shape). The same risk applies to every other abstract base class added during the rewrite.

**Mitigation**:
- Use YAGNI: the base supports EXACTLY the wire formats and lifecycles actually in use today (binary + JSON, singleton + per-key). Anything else waits for a real second use case.
- Review every new abstraction in the `simplify` skill / mode — its only purpose is "cover the cases we have today with ONE fewer code repetition".

**Detection**: code review per-PR; reject PRs that add arguments to a shared base that aren't currently exercised by any caller.

---

## R14. In-flight live deployment during the migration

**Risk**: a real production deployment is presumably running this BFF against HelloTalk's upstream. The migration must be possible to deploy behind a feature flag OR before the actual cutover — meaning both old + new code paths must work end-to-end during the transition.

**Mitigation**:
- Phase 2 DTO moves use `@JsonAlias` (or Micronaut Serde equivalent) to read either old name or new name from the upstream wire — allows zero-downtime cutover.
- Phase 4's DTO consolidations: where a record's field NAMES change, use `@JsonAlias({"oldName", "newName"})` to accept upstream's old shape during transition.
- Avoid destructive DB migrations in any in-place fix (T-1 only needs encrypt-at-rest, not column rename).

**Detection**: deploy-time feature flag flips plus a canary monitor.

---

## Summary

The biggest, hardest risks are:
- **R1 / R2 / R10**: the unofficial upstream + cross-repo frontend coupling — these are inherent to the BFF pattern and must be managed contract-first rather than refactored away.
- **R3 / R11 / R9**: the WS-connector state-machine fragility, filter ordering, and cycle detection — these are technical debt that the rewrite mitigates if done carefully, but each PR in Phase 3 needs an explicit test for these.
- **R5 / R4**: security migrations (JWT encryption, new authorization gates) — must be rolled out behind feature flags with canary monitoring.

The "good news" risk profile: none of the rewrite's risk points are unique to this codebase. Each is a well-known category with established detection and mitigation patterns.
