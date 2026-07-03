package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Everything a profile page needs in one round trip — fanned out concurrently server-side by
 * {@link com.jilali.user.ProfileBundleService}, mirroring {@code JoinBundleResponse}'s pattern
 * for rooms.
 * <p>
 * Which optional fields are populated depends on whether the requested profile is the caller's
 * own account:
 * <ul>
 *   <li>{@code isOwnProfile == true}: {@code stats} and {@code limitations} are populated
 *       (own-account edit affordances); {@code payChatInfo} and {@code reminderMoment} are
 *       {@code null} (they only make sense between two different accounts).</li>
 *   <li>{@code isOwnProfile == false}: {@code payChatInfo} and {@code reminderMoment} are
 *       populated; {@code stats} and {@code limitations} are {@code null} ({@code
 *       profile/v1/baseinfo/mnt_info} only ever returns the caller's own moment stats — there is
 *       no known way to fetch another user's, see {@code profile_endpoints.md}'s gap notes).</li>
 * </ul>
 * Only {@code userInfo} is guaranteed non-null — a failure fetching it fails the whole bundle.
 * The four optional fields degrade to {@code null} individually on their own upstream failure
 * rather than failing the bundle (see {@code ProfileBundleService} for why).
 */
@Serdeable
public record ProfileBundleResponse(
    UserInfo userInfo,
    boolean isOwnProfile,
    @Nullable ProfileStatsResponse.StatsData stats,
    @Nullable ProfileLimitationsResponse.LimitationsData limitations,
    @Nullable PayChatInfoResponse.PayChatInfoData payChatInfo,
    @Nullable ReminderMomentResponse.ReminderMomentData reminderMoment
) {}
