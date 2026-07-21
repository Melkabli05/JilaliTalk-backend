package com.jilali.roomcontext.infrastructure.client;

import com.jilali.core.JilaliException;
import com.jilali.core.JilaliProperties;
import com.jilali.roomcontext.application.port.out.StageUpstreamPort;
import com.jilali.roomcontext.infrastructure.crypto.AgoraTokenCipher;
import com.jilali.roomcontext.infrastructure.dto.stage.DeviceControlRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.KickRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.PublisherTokenResponse;
import com.jilali.roomcontext.infrastructure.dto.stage.RaiseHandApprovalRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.RaiseHandRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.StageActionRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.StageInviteApprovalRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.StageInviteRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.StageListResponse;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;

/** Dedicated Stage upstream adapter - zero dependency on the legacy client.JilaliGateway/
 *  client.JilaliClient god interface. Reproduces JilaliGateway.publisherToken()'s exact
 *  decrypt-and-validate logic natively. */
@Singleton
public class StageUpstreamAdapter implements StageUpstreamPort {

    private final StageJilaliClient client;
    private final byte[] agoraCipherKey;

    public StageUpstreamAdapter(StageJilaliClient client, JilaliProperties properties) {
        this.client = client;
        this.agoraCipherKey = properties.agoraCipherKey().getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    public StageListResponse list(int busiType, String cname) {
        return JilaliResponses.unwrap(client.stageList(busiType, cname));
    }

    @Override
    public void join(StageActionRequest request) {
        JilaliResponses.unwrap(client.stageJoin(request));
    }

    @Override
    public void quit(StageActionRequest request) {
        JilaliResponses.unwrap(client.stageQuit(request));
    }

    @Override
    public void raiseHand(RaiseHandRequest request) {
        JilaliResponses.unwrap(client.raiseHand(request));
    }

    @Override
    public void kick(KickRequest request) {
        JilaliResponses.unwrap(client.stageKick(request));
    }

    @Override
    public void raiseHandApproval(RaiseHandApprovalRequest request) {
        JilaliResponses.unwrap(client.raiseHandApproval(request));
    }

    @Override
    public void invite(StageInviteRequest request) {
        JilaliResponses.unwrap(client.stageInvite(request));
    }

    @Override
    public void inviteApproval(StageInviteApprovalRequest request) {
        JilaliResponses.unwrap(client.stageInviteApproval(request));
    }

    @Override
    public void deviceControl(DeviceControlRequest request) {
        JilaliResponses.unwrap(client.deviceControl(request));
    }

    @Override
    public PublisherTokenResponse publisherToken(String cname) {
        PublisherTokenResponse upstream = JilaliResponses.unwrap(client.publisherRtcToken(cname));
        String token = upstream != null ? upstream.token() : null;
        if (token == null || token.isBlank()) {
            throw new JilaliException(1, "Upstream returned null publisher token for " + cname, HttpStatus.BAD_GATEWAY);
        }
        return new PublisherTokenResponse(AgoraTokenCipher.decrypt(token, agoraCipherKey));
    }
}
