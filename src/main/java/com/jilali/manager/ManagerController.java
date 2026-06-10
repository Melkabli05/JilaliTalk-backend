package com.jilali.manager;

import com.jilali.client.JilaliGateway;
import com.jilali.manager.dto.ApproveManagerRequest;
import com.jilali.manager.dto.ManagerJudgeResponse;
import com.jilali.manager.dto.ManagerListResponse;
import com.jilali.manager.dto.SetManagerRequest;
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

@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/managers")
public class ManagerController {

    private final JilaliGateway liveHub;

    public ManagerController(JilaliGateway liveHub) {
        this.liveHub = liveHub;
    }

    @Get
    public ManagerListResponse list(@QueryValue @NotBlank String cname,
                                    @QueryValue("host_id") long hostId) {
        return liveHub.managerList(cname, hostId);
    }

    @Post
    public HttpResponse<Void> set(@Valid @Body SetManagerRequest request) {
        liveHub.setManagers(request);
        return HttpResponse.noContent();
    }

    @Post("/approve")
    public HttpResponse<Void> approve(
            @Valid @Body ApproveManagerRequest request) {
        liveHub.approveManager(request);
        return HttpResponse.noContent();
    }

    @Get("/judge")
    public ManagerJudgeResponse judge(
            @QueryValue @NotBlank String cname,
            @QueryValue("host_id") long hostId) {
        return liveHub.managerJudge(cname, hostId);
    }
}
