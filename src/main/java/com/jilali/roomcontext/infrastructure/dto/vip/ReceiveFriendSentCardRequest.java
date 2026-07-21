package com.jilali.roomcontext.infrastructure.dto.vip;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Positive;

@Serdeable
public record ReceiveFriendSentCardRequest(@JsonProperty("record_id") @Positive long recordId) {}
