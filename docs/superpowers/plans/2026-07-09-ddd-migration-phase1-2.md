# Backend Bounded-Context Migration — Phase 1–2 (core primitives + vip) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the shared upstream-retry policy and the current-user-id lookup out of `room`/`JilaliGateway` into `core`, then retire `JilaliGateway`'s VIP-trial business logic into a new `vip.VipService`, exactly as phased in `docs/superpowers/specs/2026-07-09-ddd-migration-design.md` (phases 1 and 2).

**Architecture:** Behavior-preserving structural refactor only — no endpoint changes any response shape or status code. `core` gains two new zero-feature-knowledge singletons (`UpstreamRetry`, `CurrentUserResolver`); `vip` gains its own `VipService` so `VipExperienceCardController` no longer depends on the shared `JilaliGateway`.

**Tech Stack:** Micronaut 5, Java 25 (`--enable-preview` for `StructuredTaskScope`), Gradle.

## Global Constraints

- No behavior changes. Every endpoint must respond byte-identically before and after each task.
- No new automated tests (there is no existing `src/test` suite; this migration does not add one — see spec's Verification section).
- Verify each task with `./gradlew compileJava` plus a manual smoke check against the running app (`./gradlew run`, then `curl`), not a test suite.
- Auth (`com.jilali.auth.*`) and `build.gradle`'s `micronaut-jdbc-hikari`/`h2`/`bcrypt` dependencies are out of scope — do not touch them.
- One commit per task. Each task must leave the app in a compiling, runnable state.

---

### Task 1: Extract the upstream-retry policy into `core.UpstreamRetry`

**Files:**
- Create: `src/main/java/com/jilali/core/UpstreamRetry.java`
- Modify: `src/main/java/com/jilali/room/RoomJoinService.java`
- Modify: `src/main/java/com/jilali/room/RoomController.java`

**Interfaces:**
- Produces: `com.jilali.core.UpstreamRetry` — `@Singleton` bean with `public <T> T call(Callable<T> call) throws Exception`. Retries on 5xx up to 4 attempts total, 700ms delay between attempts; never retries 4xx; rethrows the original `HttpClientResponseException` on final failure.

- [ ] **Step 1: Create `UpstreamRetry`**

```java
package com.jilali.core;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Retries an upstream call up to {@link #MAX_ATTEMPTS} times when Jilali returns a 5xx. A
 * just-created room's own read endpoints (stage/list, voice_room_info, user/list, comment) can
 * lag briefly behind {@code create_voice_channel} — LiveHub's own indexing hasn't caught up yet —
 * and this recovers from that without the caller needing to know about it. A 4xx is never
 * retried: that's a real error (bad request, not found) that more attempts cannot fix.
 */
@Singleton
public class UpstreamRetry {

    private static final Logger log = LoggerFactory.getLogger(UpstreamRetry.class);

    private static final int MAX_ATTEMPTS = 4;
    private static final Duration RETRY_DELAY = Duration.ofMillis(700);

    public <T> T call(Callable<T> call) throws Exception {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.call();
            } catch (HttpClientResponseException e) {
                boolean serverError = e.getStatus().getCode() >= 500;
                String upstreamBody = e.getResponse() != null
                        ? e.getResponse().getBody(String.class).orElse("<empty>")
                        : "<no response>";
                if (!serverError || attempt >= MAX_ATTEMPTS) {
                    log.warn("Upstream call failed permanently (status={}): {}", e.getStatus(), upstreamBody);
                    throw e;
                }
                log.warn("Upstream call failed (status={}), retrying attempt {}/{}: {}",
                        e.getStatus(), attempt, MAX_ATTEMPTS - 1, upstreamBody);
                Thread.sleep(RETRY_DELAY.toMillis());
            }
        }
    }
}
```

- [ ] **Step 2: Rewire `RoomJoinService` to use it instead of its own `withUpstreamRetry`**

Replace this block (constants + constructor, near the top of the class):

```java
    // A room just created via create_voice_channel is occasionally not yet visible to
    // LiveHub's own read endpoints (stage/list, voice_room_info, user/list, comment) for a
    // brief window after creation — upstream returns a bare 500 for that cname until its own
    // indexing catches up. Retrying the *individual* failing call a few times, rather than
    // the whole bundle, resolves this without re-paying for the calls that already succeeded.
    // Never retries 4xx (a real "not found" / bad request should fail immediately).
    private static final int MAX_UPSTREAM_ATTEMPTS = 4;
    private static final Duration UPSTREAM_RETRY_DELAY = Duration.ofMillis(700);

    private final JilaliClient client;
    private final JilaliProperties properties;

    public RoomJoinService(JilaliClient client, JilaliProperties properties) {
        this.client = client;
        this.properties = properties;
    }
```

with:

```java
    // See UpstreamRetry for the retry rationale: a room just created via create_voice_channel is
    // occasionally not yet visible to LiveHub's own read endpoints for a brief window.

    private final JilaliClient client;
    private final JilaliProperties properties;
    private final UpstreamRetry retry;

    public RoomJoinService(JilaliClient client, JilaliProperties properties, UpstreamRetry retry) {
        this.client = client;
        this.properties = properties;
        this.retry = retry;
    }
```

Add the import `com.jilali.core.UpstreamRetry;` and remove the now-unused `java.time.Duration` import.

Replace every call-site occurrence of `withUpstreamRetry(` with `retry.call(` (4 occurrences: the sequential `voiceInfo` fetch, `stageUsersTask`, `audienceUsersTask`, `commentsTask`). The lambda bodies are unchanged — only the receiver changes.

Delete the entire `withUpstreamRetry` method (and its javadoc) near the bottom of the class:

```java
    /**
     * Retries {@code call} up to {@link #MAX_UPSTREAM_ATTEMPTS} times when upstream returns a
     * 5xx (see the field doc above for why: a just-created room's own read endpoints can lag
     * briefly behind {@code create_voice_channel}). A 4xx is never retried — that is a real
     * error (bad request, room genuinely not found) that more attempts cannot fix.
     * <p>
     * Package-private (not {@code private}) because {@code RoomController.voiceRoomInfo} and
     * {@code liveRoomInfo} also need this same protection: the frontend's create-room flow hits
     * those single-call endpoints directly via {@code fresh=true}, and they were previously a
     * bare {@code JilaliResponses.unwrap} with no retry — so a fresh-room 5xx on the single
     * voice/live call would surface as a hard 500 and bounce the user to "Room not found".
     */
    <T> T withUpstreamRetry(Callable<T> call) throws Exception {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.call();
            } catch (HttpClientResponseException e) {
                boolean serverError = e.getStatus().getCode() >= 500;
                String upstreamBody = e.getResponse() != null
                        ? e.getResponse().getBody(String.class).orElse("<empty>")
                        : "<no response>";
                if (!serverError || attempt >= MAX_UPSTREAM_ATTEMPTS) {
                    log.warn("Upstream call failed permanently (status={}): {}", e.getStatus(), upstreamBody);
                    throw e;
                }
                log.warn("Upstream call failed (status={}), retrying attempt {}/{}: {}",
                        e.getStatus(), attempt, MAX_UPSTREAM_ATTEMPTS - 1, upstreamBody);
                Thread.sleep(UPSTREAM_RETRY_DELAY.toMillis());
            }
        }
    }
```

Note: `java.util.concurrent.Callable` stays imported in `RoomJoinService` — the lambdas passed to `retry.call(...)` are still typed as `Callable<T>` by the method signature, but since it's now `UpstreamRetry`'s own generic parameter, check whether `Callable` is still referenced by name anywhere in `RoomJoinService` after this edit (it is not — the import becomes unused and must be removed).

- [ ] **Step 3: Rewire `RoomController` to use `UpstreamRetry` directly instead of reaching into `RoomJoinService`**

Add the import `com.jilali.core.UpstreamRetry;`.

Replace the constructor:

```java
    private final JilaliClient client;
    private final JilaliProperties properties;
    private final RoomJoinService roomJoinService;
    private final RoomEventSource roomEventSource;
    private final RoomsSearchService roomsSearchService;

    public RoomController(JilaliClient client, JilaliProperties properties,
                          RoomJoinService roomJoinService, RoomEventSource roomEventSource,
                          RoomsSearchService roomsSearchService) {
        this.client = client;
        this.properties = properties;
        this.roomJoinService = roomJoinService;
        this.roomEventSource = roomEventSource;
        this.roomsSearchService = roomsSearchService;
    }
```

with:

```java
    private final JilaliClient client;
    private final JilaliProperties properties;
    private final RoomJoinService roomJoinService;
    private final RoomEventSource roomEventSource;
    private final RoomsSearchService roomsSearchService;
    private final UpstreamRetry retry;

    public RoomController(JilaliClient client, JilaliProperties properties,
                          RoomJoinService roomJoinService, RoomEventSource roomEventSource,
                          RoomsSearchService roomsSearchService, UpstreamRetry retry) {
        this.client = client;
        this.properties = properties;
        this.roomJoinService = roomJoinService;
        this.roomEventSource = roomEventSource;
        this.roomsSearchService = roomsSearchService;
        this.retry = retry;
    }
```

Replace:

```java
    /**
     * Adapts {@link RoomJoinService#withUpstreamRetry}'s checked {@code throws Exception} to an
     * unchecked signature for this controller's endpoint methods, using the same rethrow rule as
     * {@code RoomJoinService.joinBundle}'s sequential call: unwrap {@link HttpClientResponseException}
     * so {@code GlobalErrorHandler.UpstreamTransportExceptionHandler} can log upstream's real
     * response body, and wrap anything else in a {@link RuntimeException}.
     */
    private <T> T withUpstreamRetryOrThrow(Callable<T> call) {
        try {
            return roomJoinService.withUpstreamRetry(call);
        } catch (HttpClientResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Upstream fetch failed", e);
        }
    }
```

with:

```java
    /**
     * Adapts {@link UpstreamRetry#call}'s checked {@code throws Exception} to an unchecked
     * signature for this controller's endpoint methods, using the same rethrow rule as
     * {@code RoomJoinService.joinBundle}'s sequential call: unwrap {@link HttpClientResponseException}
     * so {@code GlobalErrorHandler.UpstreamTransportExceptionHandler} can log upstream's real
     * response body, and wrap anything else in a {@link RuntimeException}.
     */
    private <T> T withUpstreamRetryOrThrow(Callable<T> call) {
        try {
            return retry.call(call);
        } catch (HttpClientResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Upstream fetch failed", e);
        }
    }
```

- [ ] **Step 4: Compile**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. If it fails on an unused-import or unresolved-symbol error in `RoomJoinService.java` or `RoomController.java`, re-check the import list edits above.

- [ ] **Step 5: Manual smoke check**

```bash
./gradlew run &
sleep 5
curl -s localhost:8080/api/rooms/voice | head -c 200
curl -s "localhost:8080/api/rooms/somecname/join-bundle?busiType=2" | head -c 200
```
Expected: same response shape as before this task (a `{code,...}` upstream error or a real payload — not a 500 from a wiring break). Kill the server after (`kill %1`).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/jilali/core/UpstreamRetry.java \
        src/main/java/com/jilali/room/RoomJoinService.java \
        src/main/java/com/jilali/room/RoomController.java
git commit -m "refactor(core): extract upstream 5xx-retry policy out of RoomJoinService"
```

---

### Task 2: Extract the current-user-id lookup into `core.CurrentUserResolver`

`JilaliGateway.currentUserId()` and `ProfileBundleService`'s only other caller both need this same JWT-derived lookup; it has no room/vip/user-specific knowledge, so it belongs in `core` per the design doc's rule ("core holds only zero-feature-knowledge primitives"). This also unblocks Task 3, which needs it for `VipService.claimVipTrial()`.

**Files:**
- Create: `src/main/java/com/jilali/core/CurrentUserResolver.java`
- Modify: `src/main/java/com/jilali/user/ProfileBundleService.java`
- Modify: `src/main/java/com/jilali/client/JilaliGateway.java`

**Interfaces:**
- Produces: `com.jilali.core.CurrentUserResolver` — `@Singleton` bean with `public Long currentUserId()`.
- Consumes: `com.jilali.core.JilaliProperties#defaultAuthToken()`, `com.jilali.core.JwtUtil#uidFromBearer(String)` (both already exist, unchanged).

- [ ] **Step 1: Create `CurrentUserResolver`**

```java
package com.jilali.core;

import io.micronaut.http.context.ServerRequestContext;
import jakarta.inject.Singleton;

/**
 * Resolves the calling user's id from the JWT actually in effect for outbound Jilali calls: the
 * inbound {@code Authorization} header if the frontend sent one, else the same default token
 * {@link DefaultHeadersClientFilter} falls back to. The frontend doesn't track its own user id
 * before a room is joined (it only learns it from {@code voice_room_info}'s response), and
 * doesn't send an {@code x-ht-uid} header at all yet — so the JWT already carrying this request's
 * identity is the only reliable source.
 */
@Singleton
public class CurrentUserResolver {

    private final JilaliProperties properties;

    public CurrentUserResolver(JilaliProperties properties) {
        this.properties = properties;
    }

    public Long currentUserId() {
        var inbound = ServerRequestContext.currentRequest().orElse(null);
        String header = inbound == null ? null : inbound.getHeaders().get("authorization");
        String token = header != null && !header.isBlank() ? header : "Bearer " + properties.defaultAuthToken();
        return JwtUtil.uidFromBearer(token);
    }
}
```

- [ ] **Step 2: Rewire `ProfileBundleService`**

Add the import `com.jilali.core.CurrentUserResolver;`.

Replace:

```java
    private final JilaliGateway gateway;
    private final ProfileClient profileClient;

    public ProfileBundleService(JilaliGateway gateway, ProfileClient profileClient) {
        this.gateway = gateway;
        this.profileClient = profileClient;
    }
```

with:

```java
    private final JilaliGateway gateway;
    private final ProfileClient profileClient;
    private final CurrentUserResolver currentUserResolver;

    public ProfileBundleService(JilaliGateway gateway, ProfileClient profileClient,
                                 CurrentUserResolver currentUserResolver) {
        this.gateway = gateway;
        this.profileClient = profileClient;
        this.currentUserResolver = currentUserResolver;
    }
```

(`gateway` stays — it's still needed for `gateway.userInfo(userId)` a few lines below, unchanged in this task.)

Replace:

```java
        Long callerUid = gateway.currentUserId();
```

with:

```java
        Long callerUid = currentUserResolver.currentUserId();
```

- [ ] **Step 3: Remove `currentUserId()` from `JilaliGateway`**

Delete this method (do not delete `userInfo` or `publisherToken` — only `currentUserId`):

```java
    /**
     * Resolves the calling user's id from the JWT actually in effect for upstream calls — the
     * inbound {@code Authorization} header if the frontend sent one, else the same default token
     * {@code DefaultHeadersClientFilter} falls back to. The frontend doesn't track its own user id
     * before a room is joined (it only learns it from {@code voice_room_info}'s response), and
     * doesn't send an {@code x-ht-uid} header at all yet — so the JWT already carrying this
     * request's identity is the only reliable source. Public so callers like
     * {@link com.jilali.user.ProfileBundleService} can tell whether a requested profile is the
     * viewer's own (to decide which extra calls to fan out).
     */
    public Long currentUserId() {
        var inbound = ServerRequestContext.currentRequest().orElse(null);
        String header = inbound == null ? null : inbound.getHeaders().get("authorization");
        String token = header != null && !header.isBlank() ? header : "Bearer " + properties.defaultAuthToken();
        return JwtUtil.uidFromBearer(token);
    }
```

Remove the now-unused import `io.micronaut.http.context.ServerRequestContext;`. Do **not** remove the `JwtUtil` import — `userInfo()` still uses `JwtUtil.uidFromBearer` directly (with a different, intentionally-fixed-token argument — see that method's inline comment).

- [ ] **Step 4: Compile**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual smoke check**

```bash
./gradlew run &
sleep 5
curl -s "localhost:8080/api/profile/1/bundle" | head -c 200
```
Expected: same response shape as before (profile bundle payload or the same upstream-error shape — not a 500 from a missing bean). Kill the server after.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/jilali/core/CurrentUserResolver.java \
        src/main/java/com/jilali/user/ProfileBundleService.java \
        src/main/java/com/jilali/client/JilaliGateway.java
git commit -m "refactor(core): extract current-user-id resolution out of JilaliGateway"
```

---

### Task 3: Move VIP-trial business logic into `vip.VipService`

**Files:**
- Create: `src/main/java/com/jilali/vip/VipService.java`
- Modify: `src/main/java/com/jilali/vip/VipExperienceCardController.java`
- Modify: `src/main/java/com/jilali/client/JilaliGateway.java`

**Interfaces:**
- Consumes: `com.jilali.core.CurrentUserResolver#currentUserId()` (Task 2), `com.jilali.client.VipExperienceCardClient` (unchanged), `com.jilali.client.JilaliResponses#unwrap` (unchanged).
- Produces: `com.jilali.vip.VipService` — `@Singleton` bean with `public boolean claimVipTrial()`.

- [ ] **Step 1: Create `VipService`**

```java
package com.jilali.vip;

import com.jilali.client.JilaliResponses;
import com.jilali.client.VipExperienceCardClient;
import com.jilali.core.CurrentUserResolver;
import com.jilali.vip.dto.UseVipExperienceCardRequest;
import com.jilali.vip.dto.VipExperienceCard;
import com.jilali.vip.dto.VipExperienceCardRecordsRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VIP-perk business rules that go beyond a single pass-through call to LiveHub.
 */
@Singleton
public class VipService {

    private static final Logger log = LoggerFactory.getLogger(VipService.class);

    /** scene_id/feature_id identifying the 24h VIP trial perk on a VIP experience card. */
    private static final String VIP_TRIAL_SCENE_ID = "30000";
    private static final String VIP_TRIAL_FEATURE_ID = "00001";
    private static final String VIP_TRIAL_CARD_VERSION = "v1";

    private final VipExperienceCardClient vipClient;
    private final CurrentUserResolver currentUserResolver;

    public VipService(VipExperienceCardClient vipClient, CurrentUserResolver currentUserResolver) {
        this.vipClient = vipClient;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * Finds an unused 24h-VIP-trial perk on a card the current user owns and activates it. This
     * is the "Claim" action behind the watch-limit dialog (mirrors old_hellotalk's
     * {@code getVip24h()}) — called explicitly by the user, not silently, since it's the frontend
     * that decides whether to claim, join as a ghost listener, or leave (see
     * {@code BaseRoomStore.handleWatchLimitReached}).
     *
     * @return {@code true} if a trial was found and claimed, {@code false} if the user has none left.
     */
    public boolean claimVipTrial() {
        Long userId = currentUserResolver.currentUserId();
        if (userId == null) {
            return false;
        }
        var records = JilaliResponses.unwrap(
            vipClient.queryUserRecord(new VipExperienceCardRecordsRequest(userId, true, true)));
        if (records == null) {
            return false;
        }
        var card = records.cards().stream()
            .filter(VipService::ownsUnusedTrial)
            .findFirst();
        if (card.isEmpty()) {
            return false;
        }
        JilaliResponses.unwrap(vipClient.useCard(new UseVipExperienceCardRequest(
            card.get().id(), VIP_TRIAL_FEATURE_ID, VIP_TRIAL_SCENE_ID, userId, VIP_TRIAL_CARD_VERSION)));
        log.info("Auto-claimed 24h VIP trial for user {}", userId);
        return true;
    }

    static boolean ownsUnusedTrial(VipExperienceCard card) {
        var features = card.detail() == null ? null : card.detail().cardFeatures();
        boolean hasTrial = features != null && features.stream()
            .anyMatch(f -> VIP_TRIAL_SCENE_ID.equals(f.sceneId()) && VIP_TRIAL_FEATURE_ID.equals(f.featureId()));
        if (!hasTrial) {
            return false;
        }
        var used = card.usedFeatures();
        return used == null || used.stream()
            .noneMatch(u -> VIP_TRIAL_SCENE_ID.equals(u.sceneId()) && VIP_TRIAL_FEATURE_ID.equals(u.featureId()));
    }
}
```

- [ ] **Step 2: Rewire `VipExperienceCardController`**

Replace the import `com.jilali.client.JilaliGateway;` with nothing (delete it — `VipService` is in the same `com.jilali.vip` package as the controller, so it needs no import).

Replace:

```java
    private final VipExperienceCardClient client;
    private final JilaliGateway gateway;

    public VipExperienceCardController(VipExperienceCardClient client, JilaliGateway gateway) {
        this.client = client;
        this.gateway = gateway;
    }
```

with:

```java
    private final VipExperienceCardClient client;
    private final VipService vipService;

    public VipExperienceCardController(VipExperienceCardClient client, VipService vipService) {
        this.client = client;
        this.vipService = vipService;
    }
```

Replace:

```java
    @Post("/claim-trial")
    public ClaimVipTrialResponse claimTrial() {
        return new ClaimVipTrialResponse(gateway.claimVipTrial());
    }
```

with:

```java
    @Post("/claim-trial")
    public ClaimVipTrialResponse claimTrial() {
        return new ClaimVipTrialResponse(vipService.claimVipTrial());
    }
```

- [ ] **Step 3: Strip `JilaliGateway` down to stage helpers + `userInfo` + `publisherToken`**

Replace the entire file with:

```java
package com.jilali.client;

import com.jilali.core.JilaliException;
import com.jilali.core.JilaliProperties;
import com.jilali.core.JwtUtil;
import com.jilali.crypto.Curve25519SessionGenerator;
import com.jilali.crypto.EncbinUtil;
import com.jilali.room.AgoraTokenCipher;
import com.jilali.stage.dto.DeviceControlRequest;
import com.jilali.stage.dto.KickRequest;
import com.jilali.stage.dto.PublisherTokenResponse;
import com.jilali.stage.dto.RaiseHandApprovalRequest;
import com.jilali.stage.dto.RaiseHandRequest;
import com.jilali.stage.dto.StageActionRequest;
import com.jilali.stage.dto.StageInviteApprovalRequest;
import com.jilali.stage.dto.StageInviteRequest;
import com.jilali.stage.dto.StageListResponse;
import com.jilali.user.dto.UserInfo;
import com.jilali.user.dto.UserInfoRequest;
import com.jilali.user.dto.UserInfoResponse;
import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The seam between our application and Jilali for the calls that need more than envelope
 * unwrapping: stage action helpers, and the two calls doing real work (encrypted {@code userInfo},
 * AES-decrypted {@code publisherToken}). VIP-perk business rules live in
 * {@link com.jilali.vip.VipService}; the current-caller-id lookup lives in
 * {@link com.jilali.core.CurrentUserResolver}.
 */
@Singleton
public class JilaliGateway {

    private static final Logger log = LoggerFactory.getLogger(JilaliGateway.class);

    private final JilaliClient client;
    private final HttpClient httpClient;
    private final JilaliProperties properties;

    public JilaliGateway(JilaliClient client, @Client("jlhub") HttpClient jlhubClient, JilaliProperties properties) {
        this.client = client;
        this.httpClient = jlhubClient;
        this.properties = properties;
    }

    /** Exposes the raw client for callers that need direct envelope access. */
    public JilaliClient client() {
        return client;
    }

    // ---- Stage convenience helpers (unwrap + throw, so callers stay clean) ----

    public StageListResponse stageList(int busiType, String cname) {
        return JilaliResponses.unwrap(client.stageList(busiType, cname));
    }

    public void stageJoin(StageActionRequest body) {
        JilaliResponses.unwrap(client.stageJoin(body));
    }

    public void stageQuit(StageActionRequest body) {
        JilaliResponses.unwrap(client.stageQuit(body));
    }

    public void raiseHand(RaiseHandRequest body) {
        JilaliResponses.unwrap(client.raiseHand(body));
    }

    public void stageKick(KickRequest body) {
        JilaliResponses.unwrap(client.stageKick(body));
    }

    public void raiseHandApproval(RaiseHandApprovalRequest body) {
        JilaliResponses.unwrap(client.raiseHandApproval(body));
    }

    public void stageInvite(StageInviteRequest body) {
        JilaliResponses.unwrap(client.stageInvite(body));
    }

    public void stageInviteApproval(StageInviteApprovalRequest body) {
        JilaliResponses.unwrap(client.stageInviteApproval(body));
    }

    public void deviceControl(DeviceControlRequest body) {
        JilaliResponses.unwrap(client.deviceControl(body));
    }

    /**
     * Fetches HelloTalk user info via the encrypted ht/encbin endpoint.
     * Uses a direct HTTP call to set per-request ht/encbin headers correctly.
     * <p>
     * Cached by {@code userId} alone (see {@code user-info} cache in application.yml) — the
     * returned profile doesn't depend on which caller asked, only on whose profile it is. Every
     * room roster, comment author, and notification avatar look this same handful of user IDs
     * up repeatedly, and each miss costs a full Curve25519 handshake + AES round-trip, so a short
     * TTL removes most of that cost without serving badly stale profiles.
     *
     * @param userId the HelloTalk user ID to look up
     * @return clean UserInfo record
     */
    @Cacheable("user-info")
    public UserInfo userInfo(long userId) {
        var session = Curve25519SessionGenerator.generate(properties.serverPubKeyHex());
        var request = UserInfoRequest.forUser(userId);
        byte[] encryptedPayload = EncbinUtil.encrypt(request, session.sharedSecret());

        String token = properties.defaultAuthToken();
        String deviceId = properties.deviceId();
        // x-ht-uid must identify who is making this call (the shared service account the JWT
        // authenticates as), never the profile being looked up, and — critically — it must match
        // the uid embedded in whichever token is actually attached below. This call always uses
        // the fixed service-account token (not the caller's own forwarded JWT — CurrentUserResolver
        // prioritizes the caller's *own* inbound JWT when present, which mismatches this fixed
        // token's uid — that mismatch is why every userInfo() call upstream was rejected with
        // BAD_REQUEST). Deriving the uid from `token` itself keeps the header and the token
        // self-consistent.
        Long callerUid = JwtUtil.uidFromBearer("Bearer " + token);

        HttpRequest<byte[]> httpRequest = HttpRequest.POST("/profile/v2/userinfo", encryptedPayload)
            .header("ht-content-type", "ht/encbin")
            .header("Content-Type", "application/octet-stream")
            .header("Accept", "*/*")
            .header("Accept-Charset", "UTF-8,*;q=0.5")
            .header("Accept-Encoding", "gzip")
            .header("Accept-Language", "en-MA;q=1.0, fr-MA;q=0.9, ar-MA;q=0.8")
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "android;6.1.0;SM-A908N;11;" + (callerUid != null ? callerUid : userId))
            .header("x-ht-version", "6.1.0")
            .header("x-ht-timezone", ".00")
            .header("x-ht-tzid", "Africa/Casablanca")
            .header("x-ht-ui-mode", "1")
            .header("x-ht-channel", "google")
            .header("x-ht-device", "SM-A908N#720X1280#360#360#320#20.4")
            .header("x-ht-os-version", "11")
            .header("x-ht-os", "android")
            .header("x-ht-lang", "English")
            .header("x-ht-uid", callerUid != null ? String.valueOf(callerUid) : "")
            .header("x-ht-did", deviceId)
            .header("x-ht-build", "135")
            .header("x-ht-token", "Bearer " + token)
            .header("Authorization", "Bearer " + token)
            .header("x-ht-pub", session.headerValue());

        BlockingHttpClient blockingClient = httpClient.toBlocking();
        byte[] responseBytes;
        try {
            responseBytes = blockingClient.retrieve(httpRequest, byte[].class);
        } catch (HttpClientResponseException e) {
            String body = e.getResponse().getBody(String.class).orElse("<no body>");
            log.error("userInfo upstream error: status={}, body={}", e.getStatus(), body);
            throw new JilaliException(1, "Upstream userinfo failed: " + e.getStatus(), HttpStatus.BAD_GATEWAY);
        }
        if (responseBytes == null || responseBytes.length == 0) {
            throw new JilaliException(1, "Empty userinfo response", HttpStatus.BAD_GATEWAY);
        }
        UserInfoResponse raw = EncbinUtil.decrypt(responseBytes, session.sharedSecret(), UserInfoResponse.class);
        return raw.toUserInfo();
    }

    /**
     * Decrypts the upstream Agora publisher token for {@code cname}.
     * LiveHub returns it AES-encrypted like the join token, so it goes through
     * {@link AgoraTokenCipher#decrypt(String, byte[])}.
     */
    public PublisherTokenResponse publisherToken(String cname, byte[] agoraCipherKey) {
        PublisherTokenResponse upstream = JilaliResponses.unwrap(client.publisherRtcToken(cname));
        String token = upstream != null ? upstream.token() : null;
        if (token == null || token.isBlank()) {
            throw new JilaliException(1, "Upstream returned null publisher token for " + cname, HttpStatus.BAD_GATEWAY);
        }
        return new PublisherTokenResponse(AgoraTokenCipher.decrypt(token, agoraCipherKey));
    }
}
```

This removes the `vipClient` field/constructor param, the three `VIP_TRIAL_*` constants, and the `claimVipTrial`/`ownsUnusedTrial` methods (moved to `VipService` in Step 1), plus the now-unused `com.jilali.vip.dto.*` imports.

- [ ] **Step 4: Compile**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual smoke check**

```bash
./gradlew run &
sleep 5
curl -s -X POST localhost:8080/api/vip-experience-card/claim-trial | head -c 200
curl -s "localhost:8080/api/vip-experience-card/records?userId=1" | head -c 200
```
Expected: `claim-trial` returns `{"claimed":false}` or `{"claimed":true}` (same shape as before — depends on the test account's actual VIP-card state upstream, not on this refactor). `records` returns the same shape as before. Kill the server after.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/jilali/vip/VipService.java \
        src/main/java/com/jilali/vip/VipExperienceCardController.java \
        src/main/java/com/jilali/client/JilaliGateway.java
git commit -m "refactor(vip): move VIP-trial claim logic out of JilaliGateway into VipService"
```

---

## What's next

This plan covers spec phases 1–2 only. Phases 3–7 (`user`'s `userInfo` extraction, `comment` mapper extraction, the `room`/`stage` `JilaliClient` split, remaining contexts, and final `JilaliClient`/`JilaliGateway` deletion) each get their own plan once this one lands, per the design doc's "each phase independently landable" principle — writing them all now would go stale before they're executed and risks drifting from what Tasks 1–3 actually produce.
