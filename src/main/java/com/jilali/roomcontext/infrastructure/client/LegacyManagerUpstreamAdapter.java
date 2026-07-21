package com.jilali.roomcontext.infrastructure.client;

import com.jilali.client.JilaliClient;
import com.jilali.client.JilaliResponses;
import com.jilali.manager.dto.ApproveManagerRequest;
import com.jilali.manager.dto.ManagerJudgeResponse;
import com.jilali.manager.dto.ManagerListResponse;
import com.jilali.manager.dto.SetManagerRequest;
import com.jilali.roomcontext.application.port.out.ManagerUpstreamPort;
import jakarta.inject.Singleton;

@Singleton
public class LegacyManagerUpstreamAdapter implements ManagerUpstreamPort {

    private final JilaliClient client;

    public LegacyManagerUpstreamAdapter(JilaliClient client) {
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
