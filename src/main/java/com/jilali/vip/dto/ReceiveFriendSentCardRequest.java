package com.jilali.vip.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Positive;

@Serdeable
public record ReceiveFriendSentCardRequest(
    @JsonProperty("record_id") @Positive long recordId
) {}
