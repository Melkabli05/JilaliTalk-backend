package com.jilali.client;

import com.jilali.user.dto.FollowResultResponse;
import com.jilali.user.dto.FollowersResponse;
import com.jilali.user.dto.FollowingResponse;
import com.jilali.user.dto.LikeCountResponse;
import com.jilali.user.dto.ProfileEditRequest;
import com.jilali.user.dto.ProfileEditResponse;
import com.jilali.user.dto.ProfileMeResponse;
import com.jilali.user.dto.ProfileStatsResponse;
import com.jilali.user.dto.UserLangsResponse;
import com.jilali.user.dto.VisitorHistoryRequest;
import com.jilali.user.dto.VisitorsResponse;
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
    record FollowBody(long followUid, String nickName) {}

    /**
     * Visit request body. The real upstream requires a signed payload with
     * client metadata; we forward what the frontend sends.
     */
    @io.micronaut.serde.annotation.Serdeable
    record VisitBody(
        @Nullable String clientVer,
        String enter,
        long uid,
        long visitorUid,
        @Nullable Long clientTs,
        int updateTs,
        @Nullable String sign,
        int clientOs
    ) {}

    /** Request body for {@code POST /profile/v2/me}. */
    @io.micronaut.serde.annotation.Serdeable
    record ProfileMeBody(int rewardNotify, int studyPopup) {}

    @Post("/profile/v1/baseinfo/mnt_info")
    ProfileStatsResponse stats(@Body Map<String, Object> body);

    @Post("/user_profile_visitor/v2/my_history")
    VisitorsResponse visitors(@Body VisitorHistoryRequest body);

    @Post("/profile/v1/modify_baseinfo")
    ProfileEditResponse editProfile(@Body ProfileEditRequest body);
}