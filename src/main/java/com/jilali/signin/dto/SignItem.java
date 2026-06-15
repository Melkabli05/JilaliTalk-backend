package com.jilali.signin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SignItem(
    @JsonProperty("sign_day") int signDay,
    @JsonProperty("gift_id") int giftId,
    @JsonProperty("gift_info") String giftInfo,
    @JsonProperty("gift_type") int giftType,
    @JsonProperty("gift_number") int giftNumber,
    @JsonProperty("sign_status") int signStatus,
    @JsonProperty("to_day") boolean toDay,
    @JsonProperty("gift_name") String giftName,
    String thumb
) {}
