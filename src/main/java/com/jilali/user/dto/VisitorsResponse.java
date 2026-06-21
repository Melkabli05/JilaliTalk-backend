package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response from POST /user_profile_visitor/v2/my_history.
 * Uses msg/data envelope (not status/message).
 */
@Serdeable
public record VisitorsResponse(
    String msg,
    @Nullable VisitorsData data
) {
    @Serdeable
    public record VisitorsData(
        int index,
        boolean more,
        @Nullable List<VisitorUser> list
    ) {}

    @Serdeable
    public record VisitorUser(
        @JsonProperty("userid") long userId,
        @JsonProperty("username") String username,
        @JsonProperty("nickname") String nickname,
        @JsonProperty("nationality") String nationality,
        @JsonProperty("head_url") String headUrl,
        @Nullable String birthday,
        int sex,
        int nativeLang,
        @JsonProperty("visit_ts") long visitTs,
        @JsonProperty("visit_cnt") int visitCnt,
        @JsonProperty("is_secret_visit") boolean isSecretVisit,
        int distance,
        @JsonProperty("vip_logo") String vipLogo,
        @JsonProperty("hw_vip") boolean hwVip,
        @JsonProperty("english_ai_vip") boolean englishAiVip,
        @JsonProperty("language_ai_vip") boolean languageAiVip,
        @JsonProperty("room_status") int roomStatus,
        @JsonProperty("gift_level") int giftLevel
    ) {}
}