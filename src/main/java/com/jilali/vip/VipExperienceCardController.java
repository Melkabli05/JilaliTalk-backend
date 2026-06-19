package com.jilali.vip;

import com.jilali.client.JilaliGateway;
import com.jilali.client.JilaliResponses;
import com.jilali.client.VipExperienceCardClient;
import com.jilali.vip.dto.ClaimVipTrialResponse;
import com.jilali.vip.dto.ReceiveFriendSentCardRequest;
import com.jilali.vip.dto.UseVipExperienceCardRequest;
import com.jilali.vip.dto.VipExperienceCardRecordsRequest;
import com.jilali.vip.dto.VipExperienceCardRecordsResponse;
import com.jilali.vip.dto.VipFeatureRightRequest;
import com.jilali.vip.dto.VipFeatureRightResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.Valid;

/**
 * VIP experience cards: time-boxed perks (the 24h VIP trial is {@code sceneId=30000},
 * {@code featureId=00001}) that a user either earns or receives from a friend, then activates
 * one feature at a time via {@link #use}.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/vip-experience-card")
public class VipExperienceCardController {

    private final VipExperienceCardClient client;
    private final JilaliGateway gateway;

    public VipExperienceCardController(VipExperienceCardClient client, JilaliGateway gateway) {
        this.client = client;
        this.gateway = gateway;
    }

    @Get("/feature-right")
    public VipFeatureRightResponse featureRight(
            @QueryValue("userId") long userId,
            @QueryValue("featureId") String featureId,
            @QueryValue("sceneId") String sceneId) {
        return JilaliResponses.unwrap(
                client.queryUserFeatureRight(new VipFeatureRightRequest(userId, featureId, sceneId)));
    }

    @Get("/records")
    public VipExperienceCardRecordsResponse records(
            @QueryValue("userId") long userId,
            @QueryValue(value = "withValidFilter", defaultValue = "true") boolean withValidFilter,
            @QueryValue(value = "withDetail", defaultValue = "true") boolean withDetail) {
        return JilaliResponses.unwrap(
                client.queryUserRecord(new VipExperienceCardRecordsRequest(userId, withValidFilter, withDetail)));
    }

    /** Claims/activates one perk of an owned card — e.g. the 24h VIP trial. */
    @Post("/use")
    public HttpResponse<Void> use(@Valid @Body UseVipExperienceCardRequest request) {
        JilaliResponses.unwrap(client.useCard(request));
        return HttpResponse.noContent();
    }

    @Post("/receive-friend-card")
    public HttpResponse<Void> receiveFriendCard(@Valid @Body ReceiveFriendSentCardRequest request) {
        JilaliResponses.unwrap(client.receiveFriendSentCard(request));
        return HttpResponse.noContent();
    }

    /**
     * Finds and activates the calling user's unused 24h-VIP-trial card perk — the "Claim" action
     * behind the watch-limit dialog shown when joining a room fails with upstream code 190041.
     */
    @Post("/claim-trial")
    public ClaimVipTrialResponse claimTrial() {
        return new ClaimVipTrialResponse(gateway.claimVipTrial());
    }
}
