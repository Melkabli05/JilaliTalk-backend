package com.jilali.roomcontext.api;

import com.jilali.roomcontext.infrastructure.dto.manager.ApproveManagerRequest;
import com.jilali.roomcontext.infrastructure.dto.manager.ManagerJudgeResponse;
import com.jilali.roomcontext.infrastructure.dto.manager.ManagerListResponse;
import com.jilali.roomcontext.infrastructure.dto.manager.SetManagerRequest;
import com.jilali.roomcontext.application.port.out.ManagerUpstreamPort;
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
 *  TranslateController's Javadoc for the coexistence rationale. */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/v2/managers")
public class ManagerController {

    private final ManagerUpstreamPort upstream;

    public ManagerController(ManagerUpstreamPort upstream) {
        this.upstream = upstream;
    }

    @Get
    public ManagerListResponse list(@QueryValue @NotBlank String cname, @QueryValue("host_id") long hostId) {
        return upstream.list(cname, hostId);
    }

    @Post
    public HttpResponse<Void> set(@Valid @Body SetManagerRequest request) {
        upstream.set(request);
        return HttpResponse.noContent();
    }

    @Post("/approve")
    public HttpResponse<Void> approve(@Valid @Body ApproveManagerRequest request) {
        upstream.approve(request);
        return HttpResponse.noContent();
    }

    @Get("/judge")
    public ManagerJudgeResponse judge(@QueryValue @NotBlank String cname, @QueryValue("host_id") long hostId) {
        return upstream.judge(cname, hostId);
    }
}
