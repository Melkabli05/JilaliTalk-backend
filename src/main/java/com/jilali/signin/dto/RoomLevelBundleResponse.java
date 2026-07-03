package com.jilali.signin.dto;

import com.jilali.room.dto.RoomLevelConfigResponse;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Bundled reward + config for a room's level panel — the two calls the frontend's rewards
 * tab always makes together ({@code GET /voice/room_level/reward} and
 * {@code GET /voice/room_level/config}), fanned out concurrently server-side into one
 * round trip. See {@code RoomJoinService} for the established pattern this mirrors.
 */
@Serdeable
public record RoomLevelBundleResponse(
    RoomLevelRewardResponse reward,
    RoomLevelConfigResponse config
) {}
