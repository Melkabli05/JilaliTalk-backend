package com.jilali.roomcontext.infrastructure.client;

import com.jilali.core.AuthTokenHolder;
import com.jilali.roomcontext.application.port.out.VipUpstreamPort;
import com.jilali.roomcontext.infrastructure.dto.vip.ReceiveFriendSentCardRequest;
import com.jilali.roomcontext.infrastructure.dto.vip.UseVipExperienceCardRequest;
import com.jilali.roomcontext.infrastructure.dto.vip.VipExperienceCard;
import com.jilali.roomcontext.infrastructure.dto.vip.VipExperienceCardRecordsRequest;
import com.jilali.roomcontext.infrastructure.dto.vip.VipExperienceCardRecordsResponse;
import com.jilali.roomcontext.infrastructure.dto.vip.VipFeatureRightRequest;
import com.jilali.roomcontext.infrastructure.dto.vip.VipFeatureRightResponse;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dedicated VIP upstream adapter - implements VipUpstreamPort by calling VipJilaliClient
 *  directly, with zero dependency on the legacy client.JilaliGateway/client.JilaliClient god
 *  interface. claimTrial() reproduces JilaliGateway.claimVipTrial()'s exact algorithm (find an
 *  unused 24h-trial card among the caller's records, then activate it) natively. */
@Singleton
public class VipUpstreamAdapter implements VipUpstreamPort {

    private static final Logger log = LoggerFactory.getLogger(VipUpstreamAdapter.class);
    private static final String VIP_TRIAL_SCENE_ID = "30000";
    private static final String VIP_TRIAL_FEATURE_ID = "00001";
    private static final String VIP_TRIAL_CARD_VERSION = "v1";

    private final VipJilaliClient client;
    private final AuthTokenHolder authToken;

    public VipUpstreamAdapter(VipJilaliClient client, AuthTokenHolder authToken) {
        this.client = client;
        this.authToken = authToken;
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
        Long userId = CallerIdentity.currentUserId(authToken);
        if (userId == null) {
            return false;
        }
        var records = JilaliResponses.unwrap(
            client.queryUserRecord(new VipExperienceCardRecordsRequest(userId, true, true)));
        if (records == null) {
            return false;
        }
        var card = records.cards().stream().filter(this::ownsUnusedTrial).findFirst();
        if (card.isEmpty()) {
            return false;
        }
        JilaliResponses.unwrap(client.useCard(new UseVipExperienceCardRequest(
            card.get().id(), VIP_TRIAL_FEATURE_ID, VIP_TRIAL_SCENE_ID, userId, VIP_TRIAL_CARD_VERSION)));
        log.info("Auto-claimed 24h VIP trial for user {}", userId);
        return true;
    }

    private boolean ownsUnusedTrial(VipExperienceCard card) {
        var features = card.detail() == null ? null : card.detail().cardFeatures();
        boolean hasTrial = features != null && features.stream()
            .anyMatch(f -> VIP_TRIAL_SCENE_ID.equals(f.sceneId()) && VIP_TRIAL_FEATURE_ID.equals(f.featureId()));
        if (!hasTrial) {
            return false;
        }
        var used = card.usedFeatures();
        return used == null || used.stream()
            .noneMatch(u -> VIP_TRIAL_SCENE_ID.equals(u.sceneId()) && VIP_TRIAL_FEATURE_ID.equals(u.featureId()));
    }
}
