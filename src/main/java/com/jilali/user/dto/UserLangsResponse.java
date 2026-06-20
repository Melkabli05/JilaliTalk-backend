package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@Serdeable
public record UserLangsResponse(
    int code,
    String msg,
    @Nullable List<UserLangItem> data
) {
    @Serdeable
    public record UserLangItem(
        int lang,
        boolean isTemp,
        @JsonProperty("is_expired_vip_self_set_lang") boolean isExpiredVipSelfSetLang
    ) {}
}
