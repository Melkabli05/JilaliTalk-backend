package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Response from {@code POST /profile/v2/increment}.
 * Uses {@code status}/{@code message} envelope — unlike most of the {@code /profile/v2/*}
 * family, which use {@code code}/{@code msg}.
 * <p>
 * Reuses {@link ProfileMeResponse.Increment} for the {@code data} shape: it's the same
 * unseen-counters payload embedded in {@code profile/v2/me}, just callable standalone for
 * cheap periodic polling (tab focus, etc.) without re-fetching the whole profile bootstrap.
 */
@Serdeable
public record ProfileIncrementResponse(
    int status,
    String message,
    @Nullable ProfileMeResponse.Increment data
) {}
