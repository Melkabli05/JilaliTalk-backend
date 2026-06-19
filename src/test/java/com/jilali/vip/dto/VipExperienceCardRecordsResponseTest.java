package com.jilali.vip.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliEnvelope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VipExperienceCardRecordsResponseTest {
    private final ObjectMapper om = new ObjectMapper();

    /**
     * Captured from {@code query_user_record} before any feature on the card has been activated:
     * {@code content.cards} is populated but {@code used_features} is absent, and {@code ext} on
     * each feature is itself a JSON-encoded string rather than a nested object.
     */
    @Test
    void decodesUnusedCardWithoutThrowing() throws Exception {
        String json = """
            {"code":0,"msg":"ok","data":{"id":"000000000000000000000000","user_id":131331894,"content":{"cards":[{"id":100000027,"get_at":1781453684,"detail":{"id":28,"card_id":100000027,"c_id":28,"duration":630720000,"version":"v1","card_type":1,"card_features":[{"scene_id":"30000","feature_id":"00001","ext":"{\\"times\\":0,\\"duration\\":86400,\\"receive_use_expire_duration\\":2592000}","card_type":"2"}],"status":1,"time_type":1,"receive_use_expire_duration":2592000},"source":"","record_id":"391622"}]},"vip_status":0}}
            """;

        JilaliEnvelope<VipExperienceCardRecordsResponse> envelope =
                om.readValue(json, new TypeReference<JilaliEnvelope<VipExperienceCardRecordsResponse>>() {});

        var cards = envelope.data().cards();
        assertEquals(1, cards.size());

        var card = cards.get(0);
        assertEquals(100000027L, card.id());
        assertEquals("391622", card.recordId());
        assertEquals(0, card.usedFeatures() == null ? 0 : card.usedFeatures().size());

        var feature = card.detail().cardFeatures().get(0);
        assertEquals("30000", feature.sceneId());
        assertEquals("00001", feature.featureId());
        assertTrue(feature.ext().contains("\"duration\":86400"));
    }

    /** Captured after activating the 24h VIP perk ({@code scene_id=30000/feature_id=00001}) via {@code user_use_card}. */
    @Test
    void decodesUsedFeatureHistoryWithoutThrowing() throws Exception {
        String json = """
            {"code":0,"msg":"ok","data":{"id":"000000000000000000000000","user_id":131331894,"content":{"cards":[{"id":100000027,"get_at":1781453684,"used_features":[{"scene_id":"30000","feature_id":"00001","used_at":1781453930,"card_type":"2","relate_id":"","is_write_off":0,"used_user_version":"","source":"","push_campaign_id":"","message_id":""}],"detail":{"id":28,"card_id":100000027,"c_id":28,"duration":630720000,"version":"v1","card_type":1,"card_features":[{"scene_id":"30000","feature_id":"00001","ext":"{\\"times\\":0,\\"duration\\":86400,\\"receive_use_expire_duration\\":2592000}","card_type":"2"}],"status":1,"time_type":1,"receive_use_expire_duration":2592000},"source":"","record_id":"391622"}]},"vip_status":0}}
            """;

        JilaliEnvelope<VipExperienceCardRecordsResponse> envelope =
                om.readValue(json, new TypeReference<JilaliEnvelope<VipExperienceCardRecordsResponse>>() {});

        var usedFeature = envelope.data().cards().get(0).usedFeatures().get(0);
        assertNotNull(usedFeature);
        assertEquals("30000", usedFeature.sceneId());
        assertEquals("00001", usedFeature.featureId());
        assertEquals(1781453930L, usedFeature.usedAt());
    }

    /** Upstream sends {@code "cards": null} (not an empty array) when the user owns no cards. */
    @Test
    void treatsNullCardsAsEmptyList() throws Exception {
        String json = """
            {"code":0,"msg":"ok","data":{"id":"000000000000000000000000","user_id":131331894,"content":{"cards":null},"vip_status":0}}
            """;

        JilaliEnvelope<VipExperienceCardRecordsResponse> envelope =
                om.readValue(json, new TypeReference<JilaliEnvelope<VipExperienceCardRecordsResponse>>() {});

        assertTrue(envelope.data().cards().isEmpty());
    }
}
