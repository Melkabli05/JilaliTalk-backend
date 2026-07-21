package com.jilali.roomcontext.infrastructure.client;

import com.jilali.client.JilaliGateway;
import com.jilali.client.JilaliResponses;
import com.jilali.client.VipExperienceCardClient;
import com.jilali.roomcontext.application.port.out.VipUpstreamPort;
import com.jilali.vip.dto.ReceiveFriendSentCardRequest;
import com.jilali.vip.dto.UseVipExperienceCardRequest;
import com.jilali.vip.dto.VipExperienceCardRecordsRequest;
import com.jilali.vip.dto.VipExperienceCardRecordsResponse;
import com.jilali.vip.dto.VipFeatureRightRequest;
import com.jilali.vip.dto.VipFeatureRightResponse;
import jakarta.inject.Singleton;

/** Wraps the existing, already-correctly-scoped {@code client.VipExperienceCardClient}
 *  (its own small @Client interface, not part of the legacy god interface) and {@code
 *  client.JilaliGateway}. Same strangler-fig pattern as {@link LegacyTranslateServiceAdapter} -
 *  reusing a legacy piece that's already well-designed rather than re-declaring a duplicate
 *  @Client interface pointed at the same upstream for no benefit. */
@Singleton
public class LegacyVipExperienceCardAdapter implements VipUpstreamPort {

    private final VipExperienceCardClient client;
    private final JilaliGateway gateway;

    public LegacyVipExperienceCardAdapter(VipExperienceCardClient client, JilaliGateway gateway) {
        this.client = client;
        this.gateway = gateway;
    }

    @Override
    public VipFeatureRightResponse queryFeatureRight(VipFeatureRightRequest request) {
        return JilaliResponses.unwrap(client.queryUserFeatureRight(request));
    }

    @Override
    public VipExperienceCardRecordsResponse queryRecords(VipExperienceCardRecordsRequest request) {
        return JilaliResponses.unwrap(client.queryUserRecord(request));
    }

    @Override
    public void useCard(UseVipExperienceCardRequest request) {
        JilaliResponses.unwrap(client.useCard(request));
    }

    @Override
    public void receiveFriendSentCard(ReceiveFriendSentCardRequest request) {
        JilaliResponses.unwrap(client.receiveFriendSentCard(request));
    }

    @Override
    public boolean claimTrial() {
        return gateway.claimVipTrial();
    }
}
