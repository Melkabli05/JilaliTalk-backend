package com.jilali.roomcontext.infrastructure.client;

import com.jilali.roomcontext.application.port.out.ManagerUpstreamPort;
import com.jilali.roomcontext.infrastructure.dto.manager.ApproveManagerRequest;
import com.jilali.roomcontext.infrastructure.dto.manager.ManagerJudgeResponse;
import com.jilali.roomcontext.infrastructure.dto.manager.ManagerListResponse;
import com.jilali.roomcontext.infrastructure.dto.manager.SetManagerRequest;
import jakarta.inject.Singleton;

@Singleton
public class ManagerUpstreamAdapter implements ManagerUpstreamPort {

    private final ManagerJilaliClient client;

    public ManagerUpstreamAdapter(ManagerJilaliClient client) {
        this.client = client;
    }

    @Override
    public ManagerListResponse list(String cname, long hostId) {
        return JilaliResponses.unwrap(client.managerList(cname, hostId));
    }

    @Override
    public void set(SetManagerRequest request) {
        JilaliResponses.unwrap(client.setManagers(request));
    }

    @Override
    public void approve(ApproveManagerRequest request) {
        JilaliResponses.unwrap(client.approveManager(request));
    }

    @Override
    public ManagerJudgeResponse judge(String cname, long hostId) {
        return JilaliResponses.unwrap(client.managerJudge(cname, hostId));
    }
}
