package com.jilali.roomcontext.infrastructure.dto.user;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ProfileBundleResponse(
    UserInfo userInfo,
    boolean isOwnProfile,
    @Nullable ProfileStatsResponse.StatsData stats,
    @Nullable ProfileLimitationsResponse.LimitationsData limitations,
    @Nullable PayChatInfoResponse.PayChatInfoData payChatInfo,
    @Nullable ReminderMomentResponse.ReminderMomentData reminderMoment
) {}
