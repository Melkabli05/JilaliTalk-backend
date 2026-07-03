package com.jilali.client;

import com.jilali.user.dto.BlockListResponse;
import com.jilali.user.dto.FollowResultResponse;
import com.jilali.user.dto.FollowersResponse;
import com.jilali.user.dto.FollowingResponse;
import com.jilali.user.dto.LikeCountResponse;
import com.jilali.user.dto.PayChatInfoResponse;
import com.jilali.user.dto.ProfileEditRequest;
import com.jilali.user.dto.ProfileEditResponse;
import com.jilali.user.dto.ProfileIncrementResponse;
import com.jilali.user.dto.ProfileLimitationsResponse;
import com.jilali.user.dto.ProfileMeResponse;
import com.jilali.user.dto.ProfileStatsResponse;
import com.jilali.user.dto.ReminderMomentResponse;
import com.jilali.user.dto.UnfollowResultResponse;
import com.jilali.user.dto.UserLangsResponse;
import com.jilali.user.dto.UserTagsResponse;
import com.jilali.user.dto.VisitorHistoryRequest;
import com.jilali.user.dto.VisitorsResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import java.util.Map;

/**
 * Separate client for profile/relation endpoints that live at the API root
 * ({@code https://api-global.hellotalk8.com/}) rather than under
 * {@code /livehub/}. Uses the same {@code jlhub} HTTP client (configured with
 * the correct base URL) but without the {@code /livehub} path prefix that
 * {@link JilaliClient} adds to all its method paths.
 *
 * Response format is {@code {"status":0,"message":"success","data":...}} —
 * distinct from {@link JilaliClient}'s {@code {"code":0,"msg":"..."}} envelope.
 */
@Client(id = "jlhub", path = "")
public interface ProfileClient {

    @Post("/profile/v2/me")
    ProfileMeResponse profileMe(@Body ProfileMeBody body);

    @Get("/relation/followers")
    FollowersResponse followers(
        @QueryValue("client_os_lang") String lang,
        @QueryValue("page_index") String pageIndex,
        @QueryValue("page_size") int pageSize);

    @Get("/relation/followings")
    FollowingResponse followings(
        @QueryValue("client_os_lang") String lang,
        @QueryValue("focus_tab") int focusTab,
        @QueryValue("page_size") int pageSize,
        @QueryValue("title") String title);

    @Post("/relation/follow")
    FollowResultResponse follow(@Body FollowBody body);

    /**
     * Distinct from {@link #follow}: {@code /relation/follow} is idempotent and never
     * un-follows, verified by two consecutive live calls that both returned
     * {@code data.status:1}. Un-following requires this separate endpoint, confirmed live
     * (not present in any endpots capture) and verified by checking the target uid actually
     * dropped out of {@code /relation/followings} afterward.
     */
    @Post("/relation/unfollow")
    UnfollowResultResponse unfollow(@Body UnfollowBody body);

    @Post("/user_profile_visitor/v1/visit")
    HttpResponse<Void> recordVisit(@Body VisitBody body);

    @Get("/user_profile_visitor/v2/profile_liker_count")
    LikeCountResponse likeCount(
        @QueryValue("client_os_lang") String lang,
        @QueryValue("terminal_type") int terminalType,
        @QueryValue("uid") long uid);

    @Get("/go_user_search/v1/go_user_info/get_user_langs")
    UserLangsResponse userLangs(@QueryValue("user_id") long userId);

    /** Follow request body — mirrors the real upstream payload. */
    @io.micronaut.serde.annotation.Serdeable
    record FollowBody(
        @JsonProperty("follow_uid") long followUid,
        @JsonProperty("nick_name") String nickName
    ) {}

    /** Unfollow request body — mirrors the real upstream payload (verified live). */
    @io.micronaut.serde.annotation.Serdeable
    record UnfollowBody(
        @JsonProperty("unfollow_uid") long unfollowUid,
        @JsonProperty("nick_name") String nickName
    ) {}

    /**
     * Visit request body. The real upstream requires a signed payload with
     * client metadata; we forward what the frontend sends.
     * <p>
     * Field names are explicit {@code @JsonProperty} (not relying on a global naming strategy —
     * this codebase doesn't configure one, see {@code FollowersResponse.FollowerUser} for the
     * same pattern) because without them Serde would emit camelCase keys
     * ({@code visitorUid}, {@code clientTs}, ...) that upstream silently ignores.
     */
    @io.micronaut.serde.annotation.Serdeable
    record VisitBody(
        @JsonProperty("client_ver") @Nullable String clientVer,
        String enter,
        long uid,
        @JsonProperty("visitor_uid") long visitorUid,
        @JsonProperty("client_ts") @Nullable Long clientTs,
        @JsonProperty("update_ts") int updateTs,
        @Nullable String sign,
        @JsonProperty("client_os") int clientOs
    ) {}

    /**
     * Request body for {@code POST /profile/v2/me}. See {@link VisitBody}'s doc for why the
     * {@code @JsonProperty} annotations are required here too.
     */
    @io.micronaut.serde.annotation.Serdeable
    record ProfileMeBody(
        @JsonProperty("reward_notify") int rewardNotify,
        @JsonProperty("study_popup") int studyPopup
    ) {}

    @Post("/profile/v1/baseinfo/mnt_info")
    ProfileStatsResponse stats(@Body Map<String, Object> body);

    @Post("/user_profile_visitor/v2/my_history")
    VisitorsResponse visitors(@Body VisitorHistoryRequest body);

    @Post("/profile/v1/modify_baseinfo")
    ProfileEditResponse editProfile(@Body ProfileEditRequest body);

    @Get("/profile/v2/limitations")
    ProfileLimitationsResponse limitations();

    @Post("/profile/v2/increment")
    ProfileIncrementResponse increment(@Body IncrementBody body);

    /** Request body for {@code POST /profile/v2/increment}. */
    @io.micronaut.serde.annotation.Serdeable
    record IncrementBody(
        @JsonProperty("client_os_lang") String clientOsLang,
        String version
    ) {}

    @Get("/profile/v1/get_pay_chat_info")
    PayChatInfoResponse payChatInfo(@QueryValue("to_id") long toId);

    @Get("/profile/v1/reminder_moment")
    ReminderMomentResponse reminderMoment(@QueryValue("to") long to);

    @Get("/report_logic/v2/black/list")
    BlockListResponse blockList();

    @Get("/config-center/v1/user_tags")
    UserTagsResponse userTags(
        @QueryValue("client_lang") String clientLang,
        @QueryValue("default_tab") int defaultTab,
        @QueryValue("os_type") int osType,
        @QueryValue("version") String version);
}