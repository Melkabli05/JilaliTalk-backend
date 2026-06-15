package com.jilali.stage;

import com.jilali.client.JilaliGateway;
import com.jilali.core.JilaliProperties;
import com.jilali.stage.dto.PublisherTokenResponse;
import com.jilali.stage.dto.DeviceControlRequest;
import com.jilali.stage.dto.KickRequest;
import com.jilali.stage.dto.RaiseHandApprovalRequest;
import com.jilali.stage.dto.RaiseHandRequest;
import com.jilali.stage.dto.StageActionRequest;
import com.jilali.stage.dto.StageInviteApprovalRequest;
import com.jilali.stage.dto.StageInviteRequest;
import com.jilali.stage.dto.StageListResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.nio.charset.StandardCharsets;

@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/stage")
public class StageController {

    private final JilaliGateway gateway;
    private final byte[] agoraCipherKey;

    public StageController(JilaliGateway gateway, JilaliProperties properties) {
        this.gateway = gateway;
        this.agoraCipherKey = properties.agoraCipherKey().getBytes(StandardCharsets.US_ASCII);
    }

    @Get("/list")
    public StageListResponse list(@QueryValue(defaultValue = "2") int busiType,
                                  @QueryValue @NotBlank String cname) {
        return gateway.stageList(busiType, cname);
    }

    @Post("/join")
    public HttpResponse<Void> join(@Valid @Body StageActionRequest request) {
        gateway.stageJoin(request);
        return HttpResponse.noContent();
    }

    @Post("/quit")
    public HttpResponse<Void> quit(@Valid @Body StageActionRequest request) {
        gateway.stageQuit(request);
        return HttpResponse.noContent();
    }

    @Post("/raise-hand")
    public HttpResponse<Void> raiseHand(@Valid @Body RaiseHandRequest request) {
        gateway.raiseHand(request);
        return HttpResponse.noContent();
    }

    @Post("/kick")
    public HttpResponse<Void> kick(@Valid @Body KickRequest request) {
        gateway.stageKick(request);
        return HttpResponse.noContent();
    }

    @Post("/raise-hand/approval")
    public HttpResponse<Void> raiseHandApproval(@Valid @Body RaiseHandApprovalRequest request) {
        gateway.raiseHandApproval(request);
        return HttpResponse.noContent();
    }

    @Post("/invite")
    public HttpResponse<Void> invite(@Valid @Body StageInviteRequest request) {
        gateway.stageInvite(request);
        return HttpResponse.noContent();
    }

    @Post("/invite/approval")
    public HttpResponse<Void> inviteApproval(@Valid @Body StageInviteApprovalRequest request) {
        gateway.stageInviteApproval(request);
        return HttpResponse.noContent();
    }

    @Post("/device-control")
    public HttpResponse<Void> deviceControl(@Valid @Body DeviceControlRequest request) {
        gateway.deviceControl(request);
        return HttpResponse.noContent();
    }

    /**
     * Plain Agora token with publisher privilege for {@code cname}. The join token from
     * {@code voice_room_info} only carries subscriber rights; clients renew with this token
     * before publishing audio. LiveHub returns it AES-encrypted like the join token, so it
     * goes through the same token cipher.
     */
    @Get("/publisher-token")
    public PublisherTokenResponse publisherToken(@QueryValue @NotBlank String cname) {
        return gateway.publisherToken(cname, agoraCipherKey);
    }
}
