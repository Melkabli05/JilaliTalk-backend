package com.jilali.user;

import com.jilali.client.ProfileClient;
import com.jilali.user.dto.FollowRequest;
import com.jilali.user.dto.FollowResultResponse;
import com.jilali.user.dto.FollowersResponse;
import com.jilali.user.dto.FollowingResponse;
import com.jilali.user.dto.LikeCountResponse;
import com.jilali.user.dto.ProfileMeResponse;
import com.jilali.user.dto.UserLangsResponse;
import com.jilali.user.dto.VisitRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/profile")
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    private final ProfileClient profileClient;

    public ProfileController(ProfileClient profileClient) {
        this.profileClient = profileClient;
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