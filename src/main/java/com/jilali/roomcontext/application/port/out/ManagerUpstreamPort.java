package com.jilali.roomcontext.application.port.out;

import com.jilali.roomcontext.infrastructure.dto.manager.ApproveManagerRequest;
import com.jilali.roomcontext.infrastructure.dto.manager.ManagerJudgeResponse;
import com.jilali.roomcontext.infrastructure.dto.manager.ManagerListResponse;
import com.jilali.roomcontext.infrastructure.dto.manager.SetManagerRequest;

public interface ManagerUpstreamPort {
    ManagerListResponse list(String cname, long hostId);
    void set(SetManagerRequest request);
    void approve(ApproveManagerRequest request);
    ManagerJudgeResponse judge(String cname, long hostId);
}
