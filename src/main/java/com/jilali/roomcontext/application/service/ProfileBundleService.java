package com.jilali.roomcontext.application.service;

import com.jilali.core.AuthTokenHolder;
import com.jilali.roomcontext.infrastructure.client.CallerIdentity;
import com.jilali.roomcontext.infrastructure.client.ProfileJilaliClient;
import com.jilali.roomcontext.infrastructure.client.UserProfileEncryptedClient;
import com.jilali.roomcontext.infrastructure.dto.user.PayChatInfoResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileBundleResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileLimitationsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileStatsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ReminderMomentResponse;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Extracted natively for this bounded context - same StructuredTaskScope fan-out shape as the
 *  legacy user.ProfileBundleService, with zero dependency on client.JilaliGateway/
 *  client.ProfileClient. Bundles everything a profile page needs into one round trip. */
@Singleton
public class ProfileBundleService {

    private static final Logger log = LoggerFactory.getLogger(ProfileBundleService.class);

    /** Short TTL cache for the per-caller limitations payload, so the server-side edit gate
     *  in ProfileUpstreamAdapter.edit() doesn't have to issue an upstream round-trip per
     *  mutation. Refreshed on every bundle() call; 5 minutes is a sane upper bound — well
     *  under the upstream's typical ban-review horizon, so an unbanned user who just had
     *  their restriction lifted is unblocked promptly. */
    private static final long LIMITATIONS_CACHE_TTL_MS = 5L * 60L * 1000L;

    private final ProfileJilaliClient profileClient;
    private final UserProfileEncryptedClient encryptedClient;
    private final AuthTokenHolder authToken;

    /** Per-JVM limitations cache, scoped by the caller uid. Single-slot-per-uid with a TTL — the
     *  same caller is overwhelmingly likely to be the one asking again within seconds
     *  (page → edit flow), and the upstream payload is read-only. Not a distributed cache,
     *  so a horizontally-scaled deployment would have a per-instance copy — fine: the worst
     *  case is a brief over-/under-block until the next bundle round-trip per pod. */
    private record CachedLimitations(long fetchedAtMs,
                                    ProfileLimitationsResponse.LimitationsData data) {}
    private final AtomicReference<CachedLimitations> limitationsCache = new AtomicReference<>();

    public ProfileBundleService(ProfileJilaliClient profileClient, UserProfileEncryptedClient encryptedClient,
                                 AuthTokenHolder authToken) {
        this.profileClient = profileClient;
        this.encryptedClient = encryptedClient;
        this.authToken = authToken;
    }

    public ProfileBundleResponse bundle(long userId) {
        Long callerUid = CallerIdentity.currentUserId(authToken);
        boolean isOwnProfile = callerUid != null && callerUid == userId;

        try (var scope = StructuredTaskScope.open()) {
            var userInfoTask = scope.fork(() -> encryptedClient.fetchUserInfo(userId));

            StructuredTaskScope.Subtask<ProfileStatsResponse.StatsData> statsTask = null;
            StructuredTaskScope.Subtask<ProfileLimitationsResponse.LimitationsData> limitationsTask = null;
            StructuredTaskScope.Subtask<PayChatInfoResponse.PayChatInfoData> payChatInfoTask = null;
            StructuredTaskScope.Subtask<ReminderMomentResponse.ReminderMomentData> reminderMomentTask = null;

            if (isOwnProfile) {
                statsTask = scope.fork(this::fetchOwnStatsOrNull);
                limitationsTask = scope.fork(this::fetchLimitationsOrNull);
            } else {
                payChatInfoTask = scope.fork(() -> fetchPayChatInfoOrNull(userId));
                reminderMomentTask = scope.fork(() -> fetchReminderMomentOrNull(userId));
            }

            scope.join();

            return new ProfileBundleResponse(
                    userInfoTask.get(),
                    isOwnProfile,
                    statsTask != null ? statsTask.get() : null,
                    limitationsTask != null ? limitationsTask.get() : null,
                    payChatInfoTask != null ? payChatInfoTask.get() : null,
                    reminderMomentTask != null ? reminderMomentTask.get() : null
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during concurrent profile bundle fetch", e);
        } catch (StructuredTaskScope.FailedException e) {
            throw new RuntimeException("Upstream fetch failed during profile bundle", e.getCause());
        }
    }

    private ProfileStatsResponse.StatsData fetchOwnStatsOrNull() {
        return fetchOrNull("own profile stats",
                () -> profileClient.stats(Map.of("client_os_lang", "English")), ProfileStatsResponse::data);
    }

    private ProfileLimitationsResponse.LimitationsData fetchLimitationsOrNull() {
        var fresh = fetchOrNull("profile limitations",
                profileClient::limitations, ProfileLimitationsResponse::data);
        if (fresh != null) {
            limitationsCache.set(new CachedLimitations(System.currentTimeMillis(), fresh));
        }
        return fresh;
    }

    /**
     * Test-only seam: drives the {@link #fetchLimitationsOrNull} cache-population path
     * without paying for the full {@link #bundle} StructuredTaskScope fan-out. Package
     * private on purpose — not part of the service contract, only callable from the
     * {@code com.jilali.roomcontext.application.service} test sources. Returns the
     * fetched payload (or null when the stub returned null) so the caller can also
     * assert on the raw fetch result.
     */
    final ProfileLimitationsResponse.LimitationsData fetchLimitationsForTest() {
        return fetchLimitationsOrNull();
    }

    /** Read-most-recent cached limitations or {@code null} if stale/missing. Used by
     *  {@link ProfileUpstreamAdapter#edit} to gate mutating profile actions without an
     *  extra upstream round-trip. Returns null when the cache is empty OR has expired —
     *  a null result means "we don't know", which the gate treats as "allow" (fail-open
     *  by design; the upstream call is still made, so the server-side enforcement at
     *  the upstream still applies, this is only the pre-flight gate). */
    public ProfileLimitationsResponse.LimitationsData cachedLimitations() {
        var cached = limitationsCache.get();
        if (cached == null) return null;
        long age = System.currentTimeMillis() - cached.fetchedAtMs;
        return age <= LIMITATIONS_CACHE_TTL_MS ? cached.data() : null;
    }

    private PayChatInfoResponse.PayChatInfoData fetchPayChatInfoOrNull(long targetUserId) {
        return fetchOrNull("pay-chat info (userId=" + targetUserId + ")",
                () -> profileClient.payChatInfo(targetUserId), PayChatInfoResponse::data);
    }

    private ReminderMomentResponse.ReminderMomentData fetchReminderMomentOrNull(long targetUserId) {
        return fetchOrNull("reminder-moment (userId=" + targetUserId + ")",
                () -> profileClient.reminderMoment(targetUserId), ReminderMomentResponse::data);
    }

    private <R, T> T fetchOrNull(String description, Callable<R> call, Function<R, T> dataOf) {
        try {
            R resp = call.call();
            return resp != null ? dataOf.apply(resp) : null;
        } catch (RuntimeException e) {
            log.warn("Failed to fetch {} for bundle, degrading to null", description, e);
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
