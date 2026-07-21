package com.jilali.roomcontext.infrastructure.dto.vip;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record VipExperienceCardUsedFeature(
    @JsonProperty("scene_id") String sceneId,
    @JsonProperty("feature_id") String featureId,
    @JsonProperty("used_at") long usedAt,
    @JsonProperty("card_type") String cardType,
    @JsonProperty("relate_id") String relateId,
    @JsonProperty("is_write_off") int isWriteOff,
    @JsonProperty("used_user_version") String usedUserVersion,
    String source,
    @JsonProperty("push_campaign_id") String pushCampaignId,
    @JsonProperty("message_id") String messageId
) {}
