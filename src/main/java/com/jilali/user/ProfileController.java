package com.jilali.user;

import com.jilali.client.JilaliGateway;
import com.jilali.user.dto.FollowRequest;
import com.jilali.user.dto.FollowersResponse;
import com.jilali.user.dto.FollowingResponse;
import com.jilali.user.dto.LikeCountResponse;
import com.jilali.user.dto.ProfileMeResponse;
import com.jilali.user.dto.UserLangsResponse;
import com.jilali.user.dto.VisitRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/profile")
public class ProfileController {

    private final JilaliGateway gateway;

    public ProfileController(JilaliGateway gateway) {
        this.gateway = gateway;
    }

    @Get("/me")
    public ProfileMeResponse me() {
        return gateway.client().profileMe();
    }

    @Get("/followers")
    public FollowersResponse followers(
            @QueryValue(defaultValue = "English") String lang,
            @QueryValue(defaultValue = "") String pageIndex,
            @QueryValue(defaultValue = "20") int pageSize) {
        return gateway.client().followers(lang, pageIndex, pageSize);
    }

    @Get("/following")
    public FollowingResponse following(
            @QueryValue(defaultValue = "English") String lang,
            @QueryValue(defaultValue = "0") int focusTab,
            @QueryValue(defaultValue = "20") int pageSize,
            @QueryValue(defaultValue = "") String title) {
        return gateway.client().followings(lang, focusTab, pageSize, title);
    }

    @Post("/follow")
    public Map<String, Object> follow(@Body FollowRequest body) {
        var resp = gateway.client().follow(body);
        return Map.of(
                "status", resp.status(),
                "message", resp.message() != null ? resp.message() : "",
                "data", resp.data() != null ? resp.data() : Map.of()
        );
    }

    @Post("/visit")
    public Map<String, Object> visit(@Body VisitRequest body) {
        gateway.client().recordVisit(body);
        return Map.of("code", 0, "msg", "ok");
    }

    @Get("/like-count")
    public LikeCountResponse likeCount(
            @QueryValue(defaultValue = "English") String lang,
            @QueryValue(defaultValue = "0") int terminalType,
            @QueryValue long uid) {
        return gateway.client().likeCount(lang, terminalType, uid);
    }

    @Get("/langs")
    public UserLangsResponse langs(@QueryValue long userId) {
        return gateway.client().userLangs(userId);
    }
}
