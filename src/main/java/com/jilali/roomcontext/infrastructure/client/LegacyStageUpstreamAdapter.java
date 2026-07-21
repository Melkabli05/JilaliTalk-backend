package com.jilali.roomcontext.infrastructure.client;

import com.jilali.client.JilaliGateway;
import com.jilali.core.JilaliProperties;
import com.jilali.roomcontext.application.port.out.StageUpstreamPort;
import com.jilali.stage.dto.DeviceControlRequest;
import com.jilali.stage.dto.KickRequest;
import com.jilali.stage.dto.PublisherTokenResponse;
import com.jilali.stage.dto.RaiseHandApprovalRequest;
import com.jilali.stage.dto.RaiseHandRequest;
import com.jilali.stage.dto.StageActionRequest;
import com.jilali.stage.dto.StageInviteApprovalRequest;
import com.jilali.stage.dto.StageInviteRequest;
import com.jilali.stage.dto.StageListResponse;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;

@Singleton
public class LegacyStageUpstreamAdapter implements StageUpstreamPort {

    private final JilaliGateway gateway;
    private final byte[] agoraCipherKey;

    public LegacyStageUpstreamAdapter(JilaliGateway gateway, JilaliProperties properties) {
        this.gateway = gateway;
        this.agoraCipherKey = properties.agoraCipherKey().getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    public StageListResponse list(int busiType, String cname) {
        return gateway.stageList(busiType, cname);
    }

    @Override
    public void join(StageActionRequest request) {
        gateway.stageJoin(request);
    }

    @Override
    public void quit(StageActionRequest request) {
        gateway.stageQuit(request);
    }

    @Override
    public void raiseHand(RaiseHandRequest request) {
        gateway.raiseHand(request);
    }

    @Override
    public void kick(KickRequest request) {
        gateway.stageKick(request);
    }

    @Override
    public void raiseHandApproval(RaiseHandApprovalRequest request) {
        gateway.raiseHandApproval(request);
    }

    @Override
    public void invite(StageInviteRequest request) {
        gateway.stageInvite(request);
    }

    @Override
    public void inviteApproval(StageInviteApprovalRequest request) {
        gateway.stageInviteApproval(request);
    }

    @Override
    public void deviceControl(DeviceControlRequest request) {
        gateway.deviceControl(request);
    }

    @Override
    public PublisherTokenResponse publisherToken(String cname) {
        return gateway.publisherToken(cname, agoraCipherKey);
    }
}
