package com.jilali.roomcontext.application.port.out;

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

import java.util.Map;

public interface ProfileUpstreamPort {
    ProfileMeResponse me();
    FollowersResponse followers(String lang, String pageIndex, int pageSize);
    FollowingResponse following(String lang, int focusTab, int pageSize, String title);
    FollowResultResponse follow(FollowRequest body);
    FollowResultResponse unfollow(UnfollowRequest body);
    void visit(Map<String, Object> body);
    LikeCountResponse likeCount(String lang, int terminalType, long uid);
    UserLangsResponse langs(long userId);
    ProfileStatsResponse stats(Map<String, Object> body);
    VisitorsResponse visitors(VisitorHistoryRequest body);
    ProfileEditResponse edit(ProfileEditRequest body);
    ProfileLimitationsResponse limitations();
    ProfileIncrementResponse increment(String lang, String version);
    PayChatInfoResponse payChatInfo(long toId);
    ReminderMomentResponse reminderMoment(long to);
    BlockListResponse blocklist();
    UserTagsResponse tags(String lang, String version);
    ProfileBundleResponse bundle(long userId);
}
