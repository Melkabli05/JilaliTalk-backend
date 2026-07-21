package com.jilali.roomcontext.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.client.ProfileClient;
import com.jilali.core.AuthTokenHolder;
import com.jilali.core.JilaliProperties;
import com.jilali.core.UidExtractor;
import com.jilali.crypto.ApkSignatureGenerator;
import com.jilali.crypto.Md5Util;
import com.jilali.roomcontext.application.port.out.ProfileUpstreamPort;
import com.jilali.user.ProfileBundleService;
import com.jilali.user.dto.BlockListResponse;
import com.jilali.user.dto.FollowRequest;
import com.jilali.user.dto.FollowResultResponse;
import com.jilali.user.dto.FollowersResponse;
import com.jilali.user.dto.FollowingResponse;
import com.jilali.user.dto.LikeCountResponse;
import com.jilali.user.dto.PayChatInfoResponse;
import com.jilali.user.dto.ProfileBundleResponse;
import com.jilali.user.dto.ProfileEditRequest;
import com.jilali.user.dto.ProfileEditResponse;
import com.jilali.user.dto.ProfileIncrementResponse;
import com.jilali.user.dto.ProfileLimitationsResponse;
import com.jilali.user.dto.ProfileMeResponse;
import com.jilali.user.dto.ProfileStatsResponse;
import com.jilali.user.dto.ReminderMomentResponse;
import com.jilali.user.dto.UnfollowRequest;
import com.jilali.user.dto.UnfollowResultResponse;
import com.jilali.user.dto.UserLangsResponse;
import com.jilali.user.dto.UserTagsResponse;
import com.jilali.user.dto.VisitorHistoryRequest;
import com.jilali.user.dto.VisitorsResponse;
import jakarta.inject.Singleton;

import java.util.Map;

/** Preserves the legacy ProfileController's request-shaping logic verbatim (visit/visitor-
 *  history signing, follow/unfollow response normalization) - this logic is upstream-contract
 *  detail, not business logic that belongs in the domain, and re-deriving it from scratch would
 *  risk subtle mistakes in already-correct, previously-audited code. Same strangler-fig
 *  principle as the other adapters, applied to a case with real inline logic instead of a pure
 *  pass-through. */
@Singleton
public class LegacyProfileUpstreamAdapter implements ProfileUpstreamPort {

    private final ProfileClient profileClient;
    private final ProfileBundleService bundleService;
    private final JilaliProperties properties;
    private final AuthTokenHolder authToken;
    private final ObjectMapper om;

    public LegacyProfileUpstreamAdapter(ProfileClient profileClient, ProfileBundleService bundleService,
                                         JilaliProperties properties, AuthTokenHolder authToken, ObjectMapper om) {
        this.profileClient = profileClient;
        this.bundleService = bundleService;
        this.properties = properties;
        this.authToken = authToken;
        this.om = om;
    }

    private long callerUserId() {
        return UidExtractor.uidAsLong(authToken.get(), om);
    }

    @Override
    public ProfileMeResponse me() {
        return profileClient.profileMe(new ProfileClient.ProfileMeBody(1, 1));
    }

    @Override
    public FollowersResponse followers(String lang, String pageIndex, int pageSize) {
        return profileClient.followers(lang, pageIndex, pageSize);
    }

    @Override
    public FollowingResponse following(String lang, int focusTab, int pageSize, String title) {
        return profileClient.followings(lang, focusTab, pageSize, title);
    }

    @Override
    public FollowResultResponse follow(FollowRequest body) {
        return profileClient.follow(new ProfileClient.FollowBody(body.followUid(), body.nickName()));
    }

    @Override
    public FollowResultResponse unfollow(UnfollowRequest body) {
        UnfollowResultResponse result = profileClient.unfollow(
            new ProfileClient.UnfollowBody(body.unfollowUid(), body.nickName()));
        FollowResultResponse.FollowResultData data = result.data() == null
            ? null
            : new FollowResultResponse.FollowResultData(
                result.data().listTimestamp(), 0, 0, result.data().listTimestamp());
        return new FollowResultResponse(result.status(), result.message(), data);
    }

    @Override
    public void visit(Map<String, Object> body) {
        long uid = toLong(body.get("uid"));
        long visitorUid = toLong(body.get("visitor_uid"));
        String enter = body.getOrDefault("enter", "profile").toString();
        Integer clientOs = toInt(body.get("client_os"));
        var visitBody = new ProfileClient.VisitBody(
            (String) body.get("client_ver"), enter, uid, visitorUid,
            toLong(body.get("client_ts")), toInt(body.get("update_ts")),
            (String) body.get("sign"), clientOs != null ? clientOs : 0);
        profileClient.recordVisit(visitBody);
    }

    @Override
    public LikeCountResponse likeCount(String lang, int terminalType, long uid) {
        return profileClient.likeCount(lang, terminalType, uid);
    }

    @Override
    public UserLangsResponse langs(long userId) {
        return profileClient.userLangs(userId);
    }

    @Override
    public ProfileStatsResponse stats(Map<String, Object> body) {
        return profileClient.stats(body);
    }

    @Override
    public VisitorsResponse visitors(VisitorHistoryRequest body) {
        long clientTs = System.currentTimeMillis();
        var identifiedBody = new VisitorHistoryRequest(
            properties.deviceModel(), clientTs, body.index(), properties.deviceId(),
            Md5Util.visitorHistorySign(callerUserId(), clientTs),
            ApkSignatureGenerator.VERSION_NAME, clientTs, 0);
        return profileClient.visitors(identifiedBody);
    }

    @Override
    public ProfileEditResponse edit(ProfileEditRequest body) {
        return profileClient.editProfile(body);
    }

    @Override
    public ProfileLimitationsResponse limitations() {
        return profileClient.limitations();
    }

    @Override
    public ProfileIncrementResponse increment(String lang, String version) {
        return profileClient.increment(new ProfileClient.IncrementBody(lang, version));
    }

    @Override
    public PayChatInfoResponse payChatInfo(long toId) {
        return profileClient.payChatInfo(toId);
    }

    @Override
    public ReminderMomentResponse reminderMoment(long to) {
        return profileClient.reminderMoment(to);
    }

    @Override
    public BlockListResponse blocklist() {
        return profileClient.blockList();
    }

    @Override
    public UserTagsResponse tags(String lang, String version) {
        return profileClient.userTags(lang, 0, 0, version);
    }

    @Override
    public ProfileBundleResponse bundle(long userId) {
        return bundleService.bundle(userId);
    }

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    private static int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }
}
