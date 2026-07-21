package com.jilali.roomcontext.infrastructure.dto.user;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

@Serdeable
public record ReminderMomentResponse(int code, String msg, @Nullable ReminderMomentData data) {
    @Serdeable
    public record ReminderMomentData(
        @JsonProperty("reminder_moment_type") int reminderMomentType,
        @JsonProperty("reminder_desc") @Nullable String reminderDesc,
        @JsonProperty("button_desc") @Nullable String buttonDesc,
        @JsonProperty("after_click_desc") @Nullable String afterClickDesc
    ) {}
}
