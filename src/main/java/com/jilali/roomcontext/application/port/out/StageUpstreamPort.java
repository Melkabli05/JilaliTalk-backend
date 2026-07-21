package com.jilali.roomcontext.application.port.out;

import com.jilali.stage.dto.DeviceControlRequest;
import com.jilali.stage.dto.KickRequest;
import com.jilali.stage.dto.PublisherTokenResponse;
import com.jilali.stage.dto.RaiseHandApprovalRequest;
import com.jilali.stage.dto.RaiseHandRequest;
import com.jilali.stage.dto.StageActionRequest;
import com.jilali.stage.dto.StageInviteApprovalRequest;
import com.jilali.stage.dto.StageInviteRequest;
import com.jilali.stage.dto.StageListResponse;

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
