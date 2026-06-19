package com.jilali.vip.dto;

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
    /** Upstream returns {@code "cards": null} rather than an empty array when the user owns none. */
    public List<VipExperienceCard> cards() {
        var cards = content == null ? null : content.cards();
        return cards == null ? List.of() : cards;
    }

    @Serdeable
    public record Content(@Nullable List<VipExperienceCard> cards) {}
}
