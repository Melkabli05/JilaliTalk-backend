package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from {@code GET /profile/v1/get_pay_chat_info?to_id={userId}}.
 * Uses {@code code}/{@code msg} envelope.
 * <p>
 * Checked when opening another user's profile/chat — reports whether "paid chat" (a
 * monetization gate on message replies) applies between the viewer and that user. The actual
 * paid-chat purchase flow is out of scope here; this is read-only status.
 */
@Serdeable
public record PayChatInfoResponse(
    int code,
    String msg,
    @Nullable PayChatInfoData data
) {
    @Serdeable
    public record PayChatInfoData(
        @JsonProperty("other_side_switch") boolean otherSideSwitch,
        @JsonProperty("mine_switch") boolean mineSwitch,
        @JsonProperty("pay_relation") boolean payRelation,
        @JsonProperty("over_history_chat_count") boolean overHistoryChatCount,
        @JsonProperty("pay_val") int payVal,
        @JsonProperty("valid_time") long validTime,
        @JsonProperty("other_side_version_pay") boolean otherSideVersionPay,
        @JsonProperty("pay_val_update_ts") long payValUpdateTs,
        @JsonProperty("other_side_initiate_switch") boolean otherSideInitiateSwitch,
        @JsonProperty("mine_initiate_switch") boolean mineInitiateSwitch
    ) {}
}
