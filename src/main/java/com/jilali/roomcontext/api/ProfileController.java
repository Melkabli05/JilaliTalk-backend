package com.jilali.roomcontext.api;

import com.jilali.roomcontext.application.port.out.ProfileUpstreamPort;
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
import com.jilali.roomcontext.infrastructure.dto.user.UserLangsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.UserTagsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.VisitorHistoryRequest;
import com.jilali.roomcontext.infrastructure.dto.user.VisitorsResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.Map;

/** New-architecture controller, temporarily mounted under {@code /api/v2} - see
 *  TranslateController's Javadoc for the coexistence rationale. */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/v2/profile")
public class ProfileController {

    private final ProfileUpstreamPort upstream;

    public ProfileController(ProfileUpstreamPort upstream) {
        this.upstream = upstream;
    }

    @Get("/me")
    public ProfileMeResponse me() {
        return upstream.me();
    }

    @Get("/followers")
    public FollowersResponse followers(
            @QueryValue(defaultValue = "English") String lang,
            @QueryValue(defaultValue = "") String pageIndex,
            @QueryValue(defaultValue = "20") int pageSize) {
        return upstream.followers(lang, pageIndex, pageSize);
    }

    @Get("/following")
    public FollowingResponse following(
            @QueryValue(defaultValue = "English") String lang,
            @QueryValue(defaultValue = "0") int focusTab,
            @QueryValue(defaultValue = "20") int pageSize,
            @QueryValue(defaultValue = "") String title) {
        return upstream.following(lang, focusTab, pageSize, title);
    }

    @Post("/follow")
    public FollowResultResponse follow(@Body FollowRequest body) {
        return upstream.follow(body);
    }

    @Post("/unfollow")
    public FollowResultResponse unfollow(@Body UnfollowRequest body) {
        return upstream.unfollow(body);
    }

    @Post("/visit")
    public HttpResponse<Void> visit(@Body Map<String, Object> body) {
        upstream.visit(body);
        return HttpResponse.ok();
    }

    @Get("/like-count")
    public LikeCountResponse likeCount(
            @QueryValue(defaultValue = "English") String lang,
            @QueryValue(defaultValue = "0") int terminalType,
            @QueryValue long uid) {
        return upstream.likeCount(lang, terminalType, uid);
    }

    @Get("/langs")
    public UserLangsResponse langs(@QueryValue long userId) {
        return upstream.langs(userId);
    }

    @Post("/stats")
    public ProfileStatsResponse stats(@Body Map<String, Object> body) {
        return upstream.stats(body);
    }

    @Post("/visitors")
    public VisitorsResponse visitors(@Body VisitorHistoryRequest body) {
        return upstream.visitors(body);
    }

    @Post("/edit")
    public ProfileEditResponse edit(@Body ProfileEditRequest body) {
        return upstream.edit(body);
    }

    @Get("/limitations")
    public ProfileLimitationsResponse limitations() {
        return upstream.limitations();
    }

    @Get("/increment")
    public ProfileIncrementResponse increment(
            @QueryValue(defaultValue = "English") String lang,
            @QueryValue(defaultValue = "6.2.0") String version) {
        return upstream.increment(lang, version);
    }

    @Get("/pay-chat-info")
    public PayChatInfoResponse payChatInfo(@QueryValue("toId") long toId) {
        return upstream.payChatInfo(toId);
    }

    @Get("/reminder-moment")
    public ReminderMomentResponse reminderMoment(@QueryValue("to") long to) {
        return upstream.reminderMoment(to);
    }

    @Get("/blocklist")
    public BlockListResponse blocklist() {
        return upstream.blocklist();
    }

    @Get("/tags")
    public UserTagsResponse tags(
            @QueryValue(defaultValue = "English") String lang,
            @QueryValue(defaultValue = "6.3.0") String version) {
        return upstream.tags(lang, version);
    }

    @Get("/{userId}/bundle")
    public ProfileBundleResponse bundle(long userId) {
        return upstream.bundle(userId);
    }
}
