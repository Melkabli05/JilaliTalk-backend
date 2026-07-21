package com.jilali.roomcontext.application.port.out;

import com.jilali.roomcontext.infrastructure.dto.stage.DeviceControlRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.KickRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.PublisherTokenResponse;
import com.jilali.roomcontext.infrastructure.dto.stage.RaiseHandApprovalRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.RaiseHandRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.StageActionRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.StageInviteApprovalRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.StageInviteRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.StageListResponse;

public interface StageUpstreamPort {
    StageListResponse list(int busiType, String cname);
    void join(StageActionRequest request);
    void quit(StageActionRequest request);
    void raiseHand(RaiseHandRequest request);
    void kick(KickRequest request);
    void raiseHandApproval(RaiseHandApprovalRequest request);
    void invite(StageInviteRequest request);
    void inviteApproval(StageInviteApprovalRequest request);
    void deviceControl(DeviceControlRequest request);
    PublisherTokenResponse publisherToken(String cname);
}
