package com.jilali.roomcontext.infrastructure.dto.comment;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record CaptionSwitchRequest(
        @JsonProperty("busi_type") int busiType,
        @NotBlank String cname,
        @JsonProperty("caption_status") int captionStatus,
        @JsonProperty("is_try_out") boolean isTryOut) {
}
