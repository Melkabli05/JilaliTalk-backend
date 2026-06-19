package com.jilali.client;

import com.jilali.vip.dto.VipExperienceCard;
import com.jilali.vip.dto.VipExperienceCardDetail;
import com.jilali.vip.dto.VipExperienceCardFeature;
import com.jilali.vip.dto.VipExperienceCardUsedFeature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Card-eligibility logic behind {@link JilaliGateway#joinRoomWithAutoVipClaim}: a card is a
 * candidate for auto-claiming the 24h VIP trial only if it grants the trial feature
 * (scene_id=30000/feature_id=00001) and hasn't already used it.
 */
class JilaliGatewayTest {

    @Test
    void ownsUnusedTrial_trueWhenCardGrantsTrialAndHasNotUsedIt() {
        var card = cardWithFeatures(List.of(feature("30000", "00001")), null);
        assertTrue(JilaliGateway.ownsUnusedTrial(card));
    }

    @Test
    void ownsUnusedTrial_falseWhenCardDoesNotGrantTrial() {
        var card = cardWithFeatures(List.of(feature("20000", "00001")), null);
        assertFalse(JilaliGateway.ownsUnusedTrial(card));
    }

    @Test
    void ownsUnusedTrial_falseWhenTrialAlreadyUsed() {
        var card = cardWithFeatures(
            List.of(feature("30000", "00001")),
            List.of(usedFeature("30000", "00001")));
        assertFalse(JilaliGateway.ownsUnusedTrial(card));
    }

    @Test
    void ownsUnusedTrial_trueWhenADifferentFeatureWasUsedButTrialWasNot() {
        var card = cardWithFeatures(
            List.of(feature("30000", "00001"), feature("20000", "00001")),
            List.of(usedFeature("20000", "00001")));
        assertTrue(JilaliGateway.ownsUnusedTrial(card));
    }

    @Test
    void ownsUnusedTrial_falseWhenDetailIsMissing() {
        var card = new VipExperienceCard(1, 0, null, null, "", "1");
        assertFalse(JilaliGateway.ownsUnusedTrial(card));
    }

    private static VipExperienceCard cardWithFeatures(
            List<VipExperienceCardFeature> features, List<VipExperienceCardUsedFeature> used) {
        var detail = new VipExperienceCardDetail(1, 100000027L, 1, 630720000L, "v1", 1, features, 1, 1, 2592000L);
        return new VipExperienceCard(100000027L, 0L, detail, used, "", "1");
    }

    private static VipExperienceCardFeature feature(String sceneId, String featureId) {
        return new VipExperienceCardFeature(sceneId, featureId, "{}", "2");
    }

    private static VipExperienceCardUsedFeature usedFeature(String sceneId, String featureId) {
        return new VipExperienceCardUsedFeature(sceneId, featureId, 0L, "2", "", 0, "", "", "", "");
    }
}
