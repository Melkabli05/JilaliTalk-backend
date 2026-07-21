package com.jilali.roomcontext.api;

import com.jilali.roomcontext.application.port.out.VipUpstreamPort;
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

/** New-architecture controller, temporarily mounted under {@code /api/v2} - see
 *  TranslateController's Javadoc for the coexistence rationale. Calls straight through
 *  VipUpstreamPort (no domain aggregate reconstruction yet - see
 *  docs/room-redesign/09-technical-risks.md follow-up note on mapping upstream records into
 *  a VipExperienceCard aggregate; this slice proves the direct-@Client-wrap pattern, distinct
 *  from Translation's wrap-existing-service pattern). */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/v2/vip-experience-card")
public class VipController {

    private final VipUpstreamPort upstream;

    public VipController(VipUpstreamPort upstream) {
        this.upstream = upstream;
    }

    @Get("/feature-right")
    public VipFeatureRightResponse featureRight(
            @QueryValue("userId") long userId,
            @QueryValue("featureId") String featureId,
            @QueryValue("sceneId") String sceneId) {
        return upstream.queryFeatureRight(new VipFeatureRightRequest(userId, featureId, sceneId));
    }

    @Get("/records")
    public VipExperienceCardRecordsResponse records(
            @QueryValue("userId") long userId,
            @QueryValue(value = "withValidFilter", defaultValue = "true") boolean withValidFilter,
            @QueryValue(value = "withDetail", defaultValue = "true") boolean withDetail) {
        return upstream.queryRecords(new VipExperienceCardRecordsRequest(userId, withValidFilter, withDetail));
    }

    @Post("/use")
    public HttpResponse<Void> use(@Valid @Body UseVipExperienceCardRequest request) {
        upstream.useCard(request);
        return HttpResponse.noContent();
    }

    @Post("/receive-friend-card")
    public HttpResponse<Void> receiveFriendCard(@Valid @Body ReceiveFriendSentCardRequest request) {
        upstream.receiveFriendSentCard(request);
        return HttpResponse.noContent();
    }

    @Post("/claim-trial")
    public ClaimVipTrialResponse claimTrial() {
        return new ClaimVipTrialResponse(upstream.claimTrial());
    }
}
