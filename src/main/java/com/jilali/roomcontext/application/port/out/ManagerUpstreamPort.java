package com.jilali.roomcontext.application.port.out;

import com.jilali.manager.dto.ApproveManagerRequest;
import com.jilali.manager.dto.ManagerJudgeResponse;
import com.jilali.manager.dto.ManagerListResponse;
import com.jilali.manager.dto.SetManagerRequest;

public interface ManagerUpstreamPort {
    ManagerListResponse list(String cname, long hostId);
    void set(SetManagerRequest request);
    void approve(ApproveManagerRequest request);
    ManagerJudgeResponse judge(String cname, long hostId);
}
