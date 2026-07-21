package com.jilali.roomcontext.application.service;

import com.jilali.core.AuthTokenHolder;
import com.jilali.core.JwtUtil;
import com.jilali.roomcontext.infrastructure.client.ProfileJilaliClient;
import com.jilali.roomcontext.infrastructure.client.UserProfileEncryptedClient;
import com.jilali.roomcontext.infrastructure.dto.user.PayChatInfoResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileBundleResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileLimitationsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileStatsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ReminderMomentResponse;
import io.micronaut.http.context.ServerRequestContext;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.StructuredTaskScope;

/** Extracted natively for this bounded context - same StructuredTaskScope fan-out shape as the
 *  legacy user.ProfileBundleService, with zero dependency on client.JilaliGateway/
 *  client.ProfileClient. Bundles everything a profile page needs into one round trip. */
@Singleton
public class ProfileBundleService {

    private static final Logger log = LoggerFactory.getLogger(ProfileBundleService.class);

    private final ProfileJilaliClient profileClient;
    private final UserProfileEncryptedClient encryptedClient;
    private final AuthTokenHolder authToken;

    public ProfileBundleService(ProfileJilaliClient profileClient, UserProfileEncryptedClient encryptedClient,
                                 AuthTokenHolder authToken) {
        this.profileClient = profileClient;
        this.encryptedClient = encryptedClient;
        this.authToken = authToken;
    }

    public ProfileBundleResponse bundle(long userId) {
        Long callerUid = currentUserId();
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

    private Long currentUserId() {
        var inbound = ServerRequestContext.currentRequest().orElse(null);
        String header = inbound == null ? null : inbound.getHeaders().get("authorization");
        String token = header != null && !header.isBlank() ? header : "Bearer " + authToken.get();
        return JwtUtil.uidFromBearer(token);
    }

    private ProfileStatsResponse.StatsData fetchOwnStatsOrNull() {
        try {
            var resp = profileClient.stats(Map.of("client_os_lang", "English"));
            return resp != null ? resp.data() : null;
        } catch (RuntimeException e) {
            log.warn("Failed to fetch own profile stats for bundle, degrading to null", e);
            return null;
        }
    }

    private ProfileLimitationsResponse.LimitationsData fetchLimitationsOrNull() {
        try {
            var resp = profileClient.limitations();
            return resp != null ? resp.data() : null;
        } catch (RuntimeException e) {
            log.warn("Failed to fetch profile limitations for bundle, degrading to null", e);
            return null;
        }
    }

    private PayChatInfoResponse.PayChatInfoData fetchPayChatInfoOrNull(long targetUserId) {
        try {
            var resp = profileClient.payChatInfo(targetUserId);
            return resp != null ? resp.data() : null;
        } catch (RuntimeException e) {
            log.warn("Failed to fetch pay-chat info for bundle (userId={}), degrading to null", targetUserId, e);
            return null;
        }
    }

    private ReminderMomentResponse.ReminderMomentData fetchReminderMomentOrNull(long targetUserId) {
        try {
            var resp = profileClient.reminderMoment(targetUserId);
            return resp != null ? resp.data() : null;
        } catch (RuntimeException e) {
            log.warn("Failed to fetch reminder-moment for bundle (userId={}), degrading to null", targetUserId, e);
            return null;
        }
    }
}
