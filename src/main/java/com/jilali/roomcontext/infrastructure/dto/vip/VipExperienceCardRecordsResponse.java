package com.jilali.roomcontext.infrastructure.dto.vip;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record VipExperienceCardRecordsResponse(
    String id,
    @JsonProperty("user_id") long userId,
    Content content,
    @JsonProperty("vip_status") int vipStatus
) {
    public List<VipExperienceCard> cards() {
        var cards = content == null ? null : content.cards();
        return cards == null ? List.of() : cards;
    }

    @Serdeable
    public record Content(@Nullable List<VipExperienceCard> cards) {}
}
