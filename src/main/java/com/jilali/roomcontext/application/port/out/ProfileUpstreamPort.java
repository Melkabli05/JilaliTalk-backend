package com.jilali.roomcontext.application.port.out;

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
import com.jilali.user.dto.UserLangsResponse;
import com.jilali.user.dto.UserTagsResponse;
import com.jilali.user.dto.VisitorHistoryRequest;
import com.jilali.user.dto.VisitorsResponse;

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
