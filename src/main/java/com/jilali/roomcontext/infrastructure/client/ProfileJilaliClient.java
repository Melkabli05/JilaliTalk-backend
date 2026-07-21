package com.jilali.roomcontext.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jilali.roomcontext.infrastructure.dto.user.BlockListResponse;
import com.jilali.roomcontext.infrastructure.dto.user.FollowResultResponse;
import com.jilali.roomcontext.infrastructure.dto.user.FollowersResponse;
import com.jilali.roomcontext.infrastructure.dto.user.FollowingResponse;
import com.jilali.roomcontext.infrastructure.dto.user.LikeCountResponse;
import com.jilali.roomcontext.infrastructure.dto.user.PayChatInfoResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileEditRequest;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileEditResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileIncrementResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileLimitationsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileMeResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ProfileStatsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.ReminderMomentResponse;
import com.jilali.roomcontext.infrastructure.dto.user.UnfollowResultResponse;
import com.jilali.roomcontext.infrastructure.dto.user.UserLangsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.UserTagsResponse;
import com.jilali.roomcontext.infrastructure.dto.user.VisitorHistoryRequest;
import com.jilali.roomcontext.infrastructure.dto.user.VisitorsResponse;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

/** Dedicated Profile/relation upstream client - calls HelloTalk's API-root
 *  ({@code /profile/*}, {@code /relation/*}, {@code /user_profile_visitor/*},
 *  {@code /go_user_search/*}, {@code /report_logic/*}, {@code /config-center/*}) endpoints
 *  directly (path="" - no /livehub prefix, matching the real upstream's API-root vs livehub
 *  path split). Zero dependency on the legacy client.ProfileClient. */
@Client(id = "jlhub", path = "")
public interface ProfileJilaliClient {

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

    @Serdeable
    record FollowBody(@JsonProperty("follow_uid") long followUid, @JsonProperty("nick_name") String nickName) {}

    @Serdeable
    record UnfollowBody(@JsonProperty("unfollow_uid") long unfollowUid, @JsonProperty("nick_name") String nickName) {}

    @Serdeable
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

    @Serdeable
    record ProfileMeBody(@JsonProperty("reward_notify") int rewardNotify, @JsonProperty("study_popup") int studyPopup) {}

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

    @Serdeable
    record IncrementBody(@JsonProperty("client_os_lang") String clientOsLang, String version) {}

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
