package com.jilali.user;

import com.jilali.client.JilaliGateway;
import com.jilali.client.ProfileClient;
import com.jilali.user.dto.PayChatInfoResponse;
import com.jilali.user.dto.ProfileBundleResponse;
import com.jilali.user.dto.ProfileLimitationsResponse;
import com.jilali.user.dto.ProfileStatsResponse;
import com.jilali.user.dto.ReminderMomentResponse;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.StructuredTaskScope;

/**
 * Bundled data for a profile page — fans the calls a profile view needs out concurrently using
 * Structured Concurrency on virtual threads, mirroring {@code RoomJoinService.joinBundle}'s
 * pattern for rooms. Backs {@code GET /api/profile/{userId}/bundle}
 * ({@code ProfileController.bundle}) — the single call the frontend's profile page uses instead
 * of separately hitting {@code /users/info}, {@code /profile/stats}, {@code
 * /profile/limitations}, {@code /profile/pay-chat-info}, and {@code /profile/reminder-moment}.
 *
 * <p>Unlike a room join — where all four upstream calls are equally essential and a failure in
 * any of them means the room can't render — a profile page degrades gracefully: the user's core
 * info ({@code userInfo}) is essential and its failure fails the whole bundle, but the four
 * supplementary calls (own stats/limitations, or pay-chat-info/reminder-moment for someone
 * else's profile) are each wrapped to swallow their own upstream failure and resolve to
 * {@code null} instead — a profile page missing its moment-reminder nudge is still a usable
 * profile page. This is why those fetches don't use {@link com.jilali.client.JilaliResponses}
 * (which throws) and instead catch broadly and log.
 */
@Singleton
public class ProfileBundleService {

    private static final Logger log = LoggerFactory.getLogger(ProfileBundleService.class);

    private final JilaliGateway gateway;
    private final ProfileClient profileClient;

    public ProfileBundleService(JilaliGateway gateway, ProfileClient profileClient) {
        this.gateway = gateway;
        this.profileClient = profileClient;
    }

    /**
     * @param userId the profile being viewed — may be the caller's own account
     * @return combined payload for the profile page; {@link ProfileBundleResponse#isOwnProfile}
     *     tells the frontend which optional fields to expect populated
     * @throws RuntimeException wrapping the upstream failure if {@code userInfo} itself fails
     */
    public ProfileBundleResponse bundle(long userId) {
        Long callerUid = gateway.currentUserId();
        boolean isOwnProfile = callerUid != null && callerUid == userId;

        try (var scope = StructuredTaskScope.open()) {

            // Essential: the actual profile data. Left unwrapped (not try-caught) so its failure
            // propagates through scope.join() and fails the whole bundle, same as RoomJoinService.
            var userInfoTask = scope.fork(() -> gateway.userInfo(userId));

            // Own-profile-only: edit affordances (own moment stats, which field edits are
            // currently allowed). profile/v1/baseinfo/mnt_info only ever returns the caller's
            // own stats — there's no known way to fetch another user's, so these are skipped
            // entirely (not just left null) when viewing someone else.
            StructuredTaskScope.Subtask<ProfileStatsResponse.StatsData> statsTask = null;
            StructuredTaskScope.Subtask<ProfileLimitationsResponse.LimitationsData> limitationsTask = null;
            // Other-profile-only: the two per-viewer relationship checks that only make sense
            // between two different accounts.
            StructuredTaskScope.Subtask<PayChatInfoResponse.PayChatInfoData> payChatInfoTask = null;
            StructuredTaskScope.Subtask<ReminderMomentResponse.ReminderMomentData> reminderMomentTask = null;

            if (isOwnProfile) {
                statsTask = scope.fork(this::fetchOwnStatsOrNull);
                limitationsTask = scope.fork(this::fetchLimitationsOrNull);
            } else {
                payChatInfoTask = scope.fork(() -> fetchPayChatInfoOrNull(userId));
                reminderMomentTask = scope.fork(() -> fetchReminderMomentOrNull(userId));
            }

            // Throws StructuredTaskScope.FailedException only if userInfoTask fails — the
            // supplementary tasks above catch their own failures and resolve normally to null,
            // so they never reach join() as a failure.
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
