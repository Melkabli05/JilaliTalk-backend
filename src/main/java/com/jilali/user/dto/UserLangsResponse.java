package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response from {@code GET /go_user_search/v1/go_user_info/get_user_langs}.
 * Uses {@code code}/{@code msg} envelope.
 */
@Serdeable
public record UserLangsResponse(
    int code,
    String msg,
    @Nullable List<UserLangItem> data
) {
    @Serdeable
    public record UserLangItem(
        int lang,
        int isTemp,
        @JsonProperty("is_expired_vip_self_set_lang") int isExpiredVipSelfSetLang
    ) {}
}