package com.jilali.roomcontext.infrastructure.dto.stage;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record DeviceControlRequest(
        @NotBlank String cname,
        @JsonProperty("switch_type") int switchType,
        @JsonProperty("busi_type") int busiType,
        @JsonProperty("device_type") int deviceType) {
}
