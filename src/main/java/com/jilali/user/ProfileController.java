package com.jilali.user;

import com.jilali.client.ProfileClient;
import com.jilali.core.JilaliProperties;
import com.jilali.crypto.ApkSignatureGenerator;
import com.jilali.user.dto.BlockListResponse;
import com.jilali.user.dto.FollowRequest;
import com.jilali.user.dto.FollowResultResponse;
import com.jilali.user.dto.FollowersResponse;
import com.jilali.user.dto.FollowingResponse;
import com.jilali.user.dto.LikeCountResponse;
import com.jilali.user.dto.PayChatInfoResponse;
import com.jilali.user.dto.ProfileBundleResponse;
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
import com.jilali.user.dto.ProfileEditRequest;
import com.jilali.user.dto.ProfileEditResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/profile")
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    private final ProfileClient profileClient;
    private final ProfileBundleService bundleService;
    private final JilaliProperties properties;

    public ProfileController(ProfileClient profileClient, ProfileBundleService bundleService, JilaliProperties properties) {
        this.profileClient = profileClient;
        this.bundleService = bundleService;
        this.properties = properties;
    }

    @Get("/me")
    public ProfileMeResponse me() {
        // Real upstream expects POST with popup preference flags.
        return profileClient.profileMe(new ProfileClient.ProfileMeBody(1, 1));
    }

    @Get("/followers")
    public FollowersResponse followers(
            @QueryValue(defaultValue = "English") String lang,
            @QueryValue(defaultValue = "") String pageIndex,
            @QueryValue(defaultValue = "20") int pageSize) {
        return profileClient.followers(lang, pageIndex, pageSize);
    }

    @Get("/following")
    public FollowingResponse following(
            @QueryValue(defaultValue = "English") String lang,
            @QueryValue(defaultValue = "0") int focusTab,
            @QueryValue(defaultValue = "20") int pageSize,
            @QueryValue(defaultValue = "") String title) {
        return profileClient.followings(lang, focusTab, pageSize, title);
    }

    @Post("/follow")
    public FollowResultResponse follow(@Body FollowRequest body) {
        return profileClient.follow(
            new ProfileClient.FollowBody(body.followUid(), body.nickName()));
    }

    /**
     * Distinct upstream call from {@link #follow} — {@code /relation/follow} is idempotent,
     * not a toggle (verified live: two consecutive calls both returned {@code data.status:1}).
     * Un-following requires {@code /relation/unfollow}, whose response carries only
     * {@code list_timestamp} (see {@link UnfollowResultResponse}); normalized here into the
     * same {@link FollowResultResponse} shape {@link #follow} returns, with
     * {@code data.status:0}, so the frontend has one response type for both directions and can
     * keep reading {@code data.status} (1 = following, 0 = not) as the source of truth.
     */
    @Post("/unfollow")
    public FollowResultResponse unfollow(@Body UnfollowRequest body) {
        UnfollowResultResponse result = profileClient.unfollow(
            new ProfileClient.UnfollowBody(body.unfollowUid(), body.nickName()));
        FollowResultResponse.FollowResultData data = result.data() == null
            ? null
            : new FollowResultResponse.FollowResultData(
                result.data().listTimestamp(), 0, 0, result.data().listTimestamp());
        return new FollowResultResponse(result.status(), result.message(), data);
    }

    @Post("/visit")
    public HttpResponse<Void> visit(@Body Map<String, Object> body) {
        // The real upstream expects signed client metadata; pass through what frontend sends.
        // Cast numeric fields that may arrive as Integer.
        long uid = toLong(body.get("uid"));
        long visitorUid = toLong(body.get("visitor_uid"));
        String enter = body.getOrDefault("enter", "profile").toString();
        Integer clientOs = toInt(body.get("client_os"));
        var visitBody = new ProfileClient.VisitBody(
            (String) body.get("client_ver"),
            enter,
            uid,
            visitorUid,
            toLong(body.get("client_ts")),
            toInt(body.get("update_ts")),
            (String) body.get("sign"),
            clientOs != null ? clientOs : 0
        );
        return profileClient.recordVisit(visitBody);
    }

    @Get("/like-count")
    public LikeCountResponse likeCount(
            @QueryValue(defaultValue = "English") String lang,
            @QueryValue(defaultValue = "0") int terminalType,
            @QueryValue long uid) {
        return profileClient.likeCount(lang, terminalType, uid);
    }

    @Get("/langs")
    public UserLangsResponse langs(@QueryValue long userId) {
        return profileClient.userLangs(userId);
    }

    @Post("/stats")
    public ProfileStatsResponse stats(@Body Map<String, Object> body) {
        return profileClient.stats(body);
    }

    /**
     * The upstream ties visitor-history results to a device it recognizes for this account
     * (same device_id the IM login handshake already established, {@code DeviceIdStore})
     * — a request carrying an unrecognized device_id (e.g. a browser-generated placeholder)
     * comes back {@code 200 "no data currently"} instead of a real list or an error, so this
     * can't be left as a frontend-supplied field. Only {@code index} (pagination cursor) is
     * genuinely caller-controlled; everything else about "who we are" is this BFF's own
     * identity, matching how the IM login packet is built.
     */
    @Post("/visitors")
    public VisitorsResponse visitors(@Body VisitorHistoryRequest body) {
        var identifiedBody = new VisitorHistoryRequest(
            properties.deviceModel(),
            System.currentTimeMillis(),
            body.index(),
            properties.deviceId(),
            null,
            ApkSignatureGenerator.VERSION_NAME,
            0,
            0
        );
        return profileClient.visitors(identifiedBody);
    }

    @Post("/edit")
    public ProfileEditResponse edit(@Body ProfileEditRequest body) {
        return profileClient.editProfile(body);
    }

    @Get("/limitations")
    public ProfileLimitationsResponse limitations() {
        return profileClient.limitations();
    }

    @Get("/increment")
    public ProfileIncrementResponse increment(
            @QueryValue(defaultValue = "English") String lang,
            @QueryValue(defaultValue = "6.2.0") String version) {
        return profileClient.increment(new ProfileClient.IncrementBody(lang, version));
    }

    @Get("/pay-chat-info")
    public PayChatInfoResponse payChatInfo(@QueryValue("toId") long toId) {
        return profileClient.payChatInfo(toId);
    }

    @Get("/reminder-moment")
    public ReminderMomentResponse reminderMoment(@QueryValue("to") long to) {
        return profileClient.reminderMoment(to);
    }

    @Get("/blocklist")
    public BlockListResponse blocklist() {
        return profileClient.blockList();
    }

    @Get("/tags")
    public UserTagsResponse tags(
            @QueryValue(defaultValue = "English") String lang,
            @QueryValue(defaultValue = "6.3.0") String version) {
        return profileClient.userTags(lang, 0, 0, version);
    }

    /**
     * Everything a profile page needs in one round trip, fanned out concurrently server-side
     * ({@link ProfileBundleService#bundle}) — mirrors {@code RoomController.joinBundle}'s
     * pattern for rooms. Which extra calls get fanned out depends on whether {@code userId} is
     * the caller's own account (own stats + edit limitations) or someone else's (pay-chat gate +
     * moment-reminder nudge) — see the service for the exact self-vs-other dispatch.
     */
    @Get("/{userId}/bundle")
    public ProfileBundleResponse bundle(long userId) {
        return bundleService.bundle(userId);
    }

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.parseLong(v.toString());
    }

    private static int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        return Integer.parseInt(v.toString());
    }
}