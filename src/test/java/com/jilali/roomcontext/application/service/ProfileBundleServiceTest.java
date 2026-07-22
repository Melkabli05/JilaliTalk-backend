package com.jilali.roomcontext.application.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.jilali.core.AuthTokenHolder;
import com.jilali.core.JilaliProperties;
import com.jilali.roomcontext.infrastructure.client.ProfileJilaliClient;
import com.jilali.roomcontext.infrastructure.client.UserProfileEncryptedClient;
import com.jilali.roomcontext.infrastructure.dto.user.BlockListResponse;
import com.jilali.roomcontext.infrastructure.dto.user.FollowersResponse;
import com.jilali.roomcontext.infrastructure.dto.user.FollowingResponse;
import com.jilali.roomcontext.infrastructure.dto.user.FollowResultResponse;
import com.jilali.roomcontext.infrastructure.dto.user.LikeCountResponse;
import com.jilali.roomcontext.infrastructure.dto.user.PayChatInfoResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileEditRequest;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileEditResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileIncrementResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileLimitationsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileLimitationsResponse.LimitationsData;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileMeResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileStatsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ReminderMomentResponse;
import com.jilali.roomcontext.infrastructure.dto.user.UnfollowResultResponse;
import com.jilali.roomcontext.infrastructure.dto.user.UserLangsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.UserTagsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.VisitorHistoryRequest;
import com.jilali.roomcontext.infrastructure.dto.user.VisitorsResponse;
import io.micronaut.http.HttpResponse;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Verifies the limitations-cache contract used by {@code ProfileUpstreamAdapter.edit} to gate
 *  profile-edit requests without issuing a per-edit upstream round-trip.
 *
 *  <p>Drives the production {@link ProfileBundleService#fetchLimitationsForTest()} seam, which
 *  is package-private on purpose so test classes can exercise the cache-population path
 *  without paying for the full {@code bundle()} StructuredTaskScope fan-out.
 *
 *  <p>The {@link UserProfileEncryptedClient} is mocked (Mockito) because instantiating a real
 *  one needs an {@code io.micronaut.http.client.HttpClient} backed by a Micronaut runtime —
 *  the test deliberately avoids that DI setup so this stays a pure unit test. Mockito is on
 *  the BFF's classpath only for tests; everything else is left as a hand-rolled no-op stub
 *  (the only method that gets called here is {@code limitations()}). */
class ProfileBundleServiceTest {

    /** Implements {@link ProfileJilaliClient} but only {@code limitations()} actually returns —
     *  every other method throws {@link UnsupportedOperationException}. */
    private static final class StubProfileClient implements ProfileJilaliClient {
        private final AtomicInteger calls = new AtomicInteger(0);
        private final ProfileLimitationsResponse.LimitationsData payload;
        private final int useLimit;

        StubProfileClient(ProfileLimitationsResponse.LimitationsData payload, int useLimit) {
            this.payload = payload;
            this.useLimit = useLimit;
        }

        @Override
        public ProfileLimitationsResponse limitations() {
            int n = calls.incrementAndGet();
            if (n > useLimit) return new ProfileLimitationsResponse(0, "ok", null);
            return new ProfileLimitationsResponse(0, "ok", payload);
        }

        @Override public ProfileMeResponse profileMe(ProfileMeBody body) { throw new UnsupportedOperationException(); }
        @Override public FollowersResponse followers(String lang, String pageIndex, int pageSize) { throw new UnsupportedOperationException(); }
        @Override public FollowingResponse followings(String lang, int focusTab, int pageSize, String title) { throw new UnsupportedOperationException(); }
        @Override public FollowResultResponse follow(FollowBody body) { throw new UnsupportedOperationException(); }
        @Override public UnfollowResultResponse unfollow(UnfollowBody body) { throw new UnsupportedOperationException(); }
        @Override public HttpResponse<Void> recordVisit(VisitBody body) { throw new UnsupportedOperationException(); }
        @Override public LikeCountResponse likeCount(String lang, int terminalType, long uid) { throw new UnsupportedOperationException(); }
        @Override public UserLangsResponse userLangs(long userId) { throw new UnsupportedOperationException(); }
        @Override public ProfileStatsResponse stats(Map<String, Object> body) { throw new UnsupportedOperationException(); }
        @Override public VisitorsResponse visitors(VisitorHistoryRequest body) { throw new UnsupportedOperationException(); }
        @Override public ProfileEditResponse editProfile(ProfileEditRequest body) { throw new UnsupportedOperationException(); }
        @Override public ProfileIncrementResponse increment(IncrementBody body) { throw new UnsupportedOperationException(); }
        @Override public PayChatInfoResponse payChatInfo(long toId) { throw new UnsupportedOperationException(); }
        @Override public ReminderMomentResponse reminderMoment(long to) { throw new UnsupportedOperationException(); }
        @Override public BlockListResponse blockList() { throw new UnsupportedOperationException(); }
        @Override public UserTagsResponse userTags(String clientLang, int defaultTab, int osType, String version) { throw new UnsupportedOperationException(); }
    }

    private static LimitationsData sample(boolean restricted) {
        return new LimitationsData(
            null, null,
            true, false, true, false,
            restricted
        );
    }

    private static ProfileBundleService buildService(StubProfileClient stub) {
        var encrypted = mock(UserProfileEncryptedClient.class);
        var properties = new JilaliProperties(
            java.util.List.of(),  // forwardedHeaders
            null,                 // defaultAuthToken
            null,                 // agoraCipherKey
            null,                 // serverPubKeyHex
            null,                 // translateServerPubKeyHex
            null,                 // deviceId
            null,                 // deviceModel
            null,                 // allowedWebSocketOrigins
            null,                 // hellotalkEmail
            null                  // hellotalkPassword
        );
        var authToken = new AuthTokenHolder(properties);
        return new ProfileBundleService(stub, encrypted, authToken);
    }

    @Test
    void cachedLimitationsReturnsCachedReferenceWithinTtl() {
        var svc = buildService(new StubProfileClient(sample(true), 1));

        LimitationsData first = svc.fetchLimitationsForTest();
        assertNotNull(first);
        assertTrue(first.isModifyRestricted(), "fixture: restricted=true must round-trip");

        LimitationsData second = svc.cachedLimitations();
        assertSame(first, second, "cachedLimitations() must return the same instance until TTL expires");
    }

    @Test
    void cachedLimitationsReturnsNullWhenCacheEmpty() {
        // useLimit=0 means the stub never returns the payload — first call gets null
        var svc = buildService(new StubProfileClient(sample(false), 0));
        assertNull(svc.fetchLimitationsForTest());
        assertNull(svc.cachedLimitations(), "no successful fetch → cache stays null");
    }

    @Test
    void cachedLimitationsStaysNullWhenUpstreamReturnsNull() {
        // useLimit=1 with a null payload in the stub — exercises the null-data path
        var svc = buildService(new StubProfileClient(null, 0));
        assertNull(svc.fetchLimitationsForTest(), "fixture sanity: null data from upstream");
        assertNull(svc.cachedLimitations(), "must NOT cache a null payload — the edit gate treats null as fail-open");
    }
}
