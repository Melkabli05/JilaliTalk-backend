package com.jilali.signin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record RewardItem(
    int id,
    @JsonProperty("gift_id") int giftId,
    int type,
    @JsonProperty("card_type") int cardType,
    String name,
    int number,
    String icon,
    @JsonProperty("multi_name") String multiName
) {}
