package com.jilali.roomcontext.infrastructure.client;

import com.jilali.core.JilaliEnvelope;
import com.jilali.roomcontext.infrastructure.dto.manager.ApproveManagerRequest;
import com.jilali.roomcontext.infrastructure.dto.manager.ManagerJudgeResponse;
import com.jilali.roomcontext.infrastructure.dto.manager.ManagerListResponse;
import com.jilali.roomcontext.infrastructure.dto.manager.SetManagerRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

/** Dedicated Manager (moderator role) upstream client - calls HelloTalk's
 *  {@code /livehub/user/manager_*} endpoints directly. Zero dependency on the legacy
 *  client.JilaliClient god interface. */
@Client(id = "jlhub", path = "/livehub")
public interface ManagerJilaliClient {

    @Get("/user/manager_list")
    JilaliEnvelope<ManagerListResponse> managerList(@QueryValue String cname, @QueryValue("host_id") long hostId);

    @Post("/user/set_managers")
    JilaliEnvelope<Object> setManagers(@Body SetManagerRequest body);

    @Post("/user/approve_manager")
    JilaliEnvelope<Object> approveManager(@Body ApproveManagerRequest body);

    @Get("/user/manager_judge")
    JilaliEnvelope<ManagerJudgeResponse> managerJudge(@QueryValue String cname, @QueryValue("host_id") long hostId);
}
