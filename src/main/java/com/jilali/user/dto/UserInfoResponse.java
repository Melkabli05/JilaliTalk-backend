package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Mirrors the encrypted HelloTalk userinfo response structure.
 * The outer envelope is {@code {"code":0,"msg":"ok","data":{...}}}.
 * The data portion is deserialized from ht/encbin bytes.
 */
@Serdeable
public record UserInfoResponse(
    int code,
    @Nullable String msg,
    @Nullable UserInfoData data
) {
    @Serdeable
    public record UserInfoData(
        @JsonProperty("list") List<UserInfoItem> list
    ) {}

    @Serdeable
    public record UserInfoItem(
        @JsonProperty("user_id") long userId,
        @Nullable BaseInfo base,
        @Nullable LocationInfo location,
        @Nullable OnlineStateInfo online_state,
        @Nullable LiveStateInfo live_state
    ) {}

    @Serdeable
    public record BaseInfo(
        @Nullable String username,
        @Nullable String nickname,
        @Nullable String birthday,
        @JsonProperty("account_type") @Nullable String accountType,
        @JsonProperty("full_py") @Nullable String fullPy,
        @Nullable Integer age,
        @Nullable Integer sex,
        @Nullable String nationality,
        @JsonProperty("reg_days") @Nullable Integer regDays
    ) {}

    @Serdeable
    public record LocationInfo(
        @Nullable String city,
        @JsonProperty("full_country") @Nullable String fullCountry
    ) {}

    @Serdeable
    public record OnlineStateInfo(
        @JsonProperty("area_code") @Nullable String areaCode
    ) {}

    @Serdeable
    public record LiveStateInfo(
        @Nullable String cname
    ) {}

    public UserInfo toUserInfo() {
        if (data == null || data.list() == null || data.list().isEmpty()) {
            return null;
        }
        var item = data.list().get(0);
        return new UserInfo(
            item.userId(),
            item.base() != null ? item.base().username() : null,
            item.base() != null ? item.base().nickname() : null,
            item.base() != null ? item.base().birthday() : null,
            item.base() != null ? item.base().accountType() : null,
            item.base() != null ? item.base().fullPy() : null,
            item.base() != null ? item.base().age() : null,
            item.base() != null && item.base().sex() != null ? UserInfo.mapSex(item.base().sex()) : null,
            item.base() != null ? item.base().nationality() : null,
            item.location() != null ? item.location().city() : null,
            item.location() != null ? item.location().fullCountry() : null,
            item.online_state() != null ? item.online_state().areaCode() : null,
            item.base() != null ? item.base().regDays() : null,
            item.live_state() != null ? item.live_state().cname() : null
        );
    }
}