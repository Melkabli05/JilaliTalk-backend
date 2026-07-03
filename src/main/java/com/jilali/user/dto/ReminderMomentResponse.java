package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from {@code GET /profile/v1/reminder_moment?to={userId}}.
 * Uses {@code code}/{@code msg} envelope.
 * <p>
 * When viewing another user's profile and they haven't posted a Moment recently, this returns
 * copy for a "nudge them to post" CTA. {@code reminderMomentType == 0} means no nudge should be
 * shown (all the string fields are empty in that case).
 */
@Serdeable
public record ReminderMomentResponse(
    int code,
    String msg,
    @Nullable ReminderMomentData data
) {
    @Serdeable
    public record ReminderMomentData(
        @JsonProperty("reminder_moment_type") int reminderMomentType,
        @JsonProperty("reminder_desc") @Nullable String reminderDesc,
        @JsonProperty("button_desc") @Nullable String buttonDesc,
        @JsonProperty("after_click_desc") @Nullable String afterClickDesc
    ) {}
}
