package com.jilali.roomcontext.api;

import com.jilali.roomcontext.application.port.out.StageUpstreamPort;
import com.jilali.roomcontext.infrastructure.dto.stage.DeviceControlRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.KickRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.PublisherTokenResponse;
import com.jilali.roomcontext.infrastructure.dto.stage.RaiseHandApprovalRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.RaiseHandRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.StageActionRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.StageInviteApprovalRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.StageInviteRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.StageListResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** New-architecture controller, temporarily mounted under {@code /api/v2} - see
 *  TranslateController's Javadoc for the coexistence rationale. Calls straight through
 *  StageUpstreamPort (matching legacy behavior exactly) rather than the domain.model.Stage
 *  aggregate built in Phase 2 - same honest scope note as VipController: the domain aggregate's
 *  state machine exists and is unit-testable in isolation, but wiring it in front of the real
 *  upstream call (which requires reconstructing Stage from a "list occupants" response) is
 *  follow-up work. */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/v2/stage")
public class StageController {

    private final StageUpstreamPort upstream;

    public StageController(StageUpstreamPort upstream) {
        this.upstream = upstream;
    }

    @Get("/list")
    public StageListResponse list(@QueryValue(defaultValue = "2") int busiType, @QueryValue @NotBlank String cname) {
        return upstream.list(busiType, cname);
    }

    @Post("/join")
    public HttpResponse<Void> join(@Valid @Body StageActionRequest request) {
        upstream.join(request);
        return HttpResponse.noContent();
    }

    @Post("/quit")
    public HttpResponse<Void> quit(@Valid @Body StageActionRequest request) {
        upstream.quit(request);
        return HttpResponse.noContent();
    }

    @Post("/raise-hand")
    public HttpResponse<Void> raiseHand(@Valid @Body RaiseHandRequest request) {
        upstream.raiseHand(request);
        return HttpResponse.noContent();
    }

    @Post("/kick")
    public HttpResponse<Void> kick(@Valid @Body KickRequest request) {
        upstream.kick(request);
        return HttpResponse.noContent();
    }

    @Post("/raise-hand/approval")
    public HttpResponse<Void> raiseHandApproval(@Valid @Body RaiseHandApprovalRequest request) {
        upstream.raiseHandApproval(request);
        return HttpResponse.noContent();
    }

    @Post("/invite")
    public HttpResponse<Void> invite(@Valid @Body StageInviteRequest request) {
        upstream.invite(request);
        return HttpResponse.noContent();
    }

    @Post("/invite/approval")
    public HttpResponse<Void> inviteApproval(@Valid @Body StageInviteApprovalRequest request) {
        upstream.inviteApproval(request);
        return HttpResponse.noContent();
    }

    @Post("/device-control")
    public HttpResponse<Void> deviceControl(@Valid @Body DeviceControlRequest request) {
        upstream.deviceControl(request);
        return HttpResponse.noContent();
    }

    @Get("/publisher-token")
    public PublisherTokenResponse publisherToken(@QueryValue @NotBlank String cname) {
        return upstream.publisherToken(cname);
    }
}
