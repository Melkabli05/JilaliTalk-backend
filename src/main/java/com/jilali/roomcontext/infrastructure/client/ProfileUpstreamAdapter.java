package com.jilali.roomcontext.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.AuthTokenHolder;
import com.jilali.core.JilaliProperties;
import com.jilali.core.UidExtractor;
import com.jilali.crypto.ApkSignatureGenerator;
import com.jilali.crypto.Md5Util;
import com.jilali.roomcontext.application.port.out.ProfileUpstreamPort;
import com.jilali.roomcontext.application.service.ProfileBundleService;
import com.jilali.roomcontext.infrastructure.dto.user.BlockListResponse;
import com.jilali.roomcontext.infrastructure.dto.user.FollowRequest;
import com.jilali.roomcontext.infrastructure.dto.user.FollowResultResponse;
import com.jilali.roomcontext.infrastructure.dto.user.FollowersResponse;
import com.jilali.roomcontext.infrastructure.dto.user.FollowingResponse;
import com.jilali.roomcontext.infrastructure.dto.user.LikeCountResponse;
import com.jilali.roomcontext.infrastructure.dto.user.PayChatInfoResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileBundleResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileEditRequest;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileEditResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileIncrementResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileLimitationsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileMeResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileStatsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ReminderMomentResponse;
import com.jilali.roomcontext.infrastructure.dto.user.UnfollowRequest;
import com.jilali.roomcontext.infrastructure.dto.user.UnfollowResultResponse;
import com.jilali.roomcontext.infrastructure.dto.user.UserLangsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.UserTagsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.VisitorHistoryRequest;
import com.jilali.roomcontext.infrastructure.dto.user.VisitorsResponse;
import jakarta.inject.Singleton;

import java.util.Map;

/** Dedicated Profile/relation upstream adapter - preserves the legacy ProfileController's
 *  request-shaping logic verbatim (visit/visitor-history signing, follow/unfollow response
 *  normalization), with zero dependency on client.ProfileClient/client.JilaliGateway. */
@Singleton
public class ProfileUpstreamAdapter implements ProfileUpstreamPort {

    private final ProfileJilaliClient profileClient;
    private final ProfileBundleService bundleService;
    private final JilaliProperties properties;
    private final AuthTokenHolder authToken;
    private final ObjectMapper om;

    public ProfileUpstreamAdapter(ProfileJilaliClient profileClient, ProfileBundleService bundleService,
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
        return profileClient.profileMe(new ProfileJilaliClient.ProfileMeBody(1, 1));
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
        return profileClient.follow(new ProfileJilaliClient.FollowBody(body.followUid(), body.nickName()));
    }

    @Override
    public FollowResultResponse unfollow(UnfollowRequest body) {
        UnfollowResultResponse result = profileClient.unfollow(
            new ProfileJilaliClient.UnfollowBody(body.unfollowUid(), body.nickName()));
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
        var visitBody = new ProfileJilaliClient.VisitBody(
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
        return profileClient.increment(new ProfileJilaliClient.IncrementBody(lang, version));
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
