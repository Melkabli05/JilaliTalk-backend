package com.jilali.roomcontext.infrastructure.dto.user;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record UserInfoRequest(List<String> groups, String source, List<Long> user_ids) {
    public UserInfoRequest {
        if (groups == null) groups = List.of();
        if (user_ids == null) user_ids = List.of();
    }

    public static UserInfoRequest forUser(long userId) {
        return new UserInfoRequest(
            List.of("base", "default", "location", "online_state",
                "points", "privileges", "relation", "tags",
                "live_state", "remark", "gift_level",
                "black_stat", "study_punch", "pay_info", "pay_exposure"),
            "OtherProfileActivity#null",
            List.of(userId));
    }
}
