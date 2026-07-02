package com.jilali.room.dto;

import com.jilali.stage.dto.StageListResponse;
import com.jilali.user.dto.RoomUserListResponse;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Bundled result of the three calls needed to join a room, fanned out to upstream in parallel
 * from the BFF so the browser makes a single round-trip instead of three sequential ones.
 *
 * @param voiceRoomInfo  room metadata including decrypted RTC token for Agora
 * @param stageUsers    on-stage members
 * @param audienceUsers  audience roster
 */
@Serdeable
public record JoinBundleResponse(
    VoiceRoomInfoResponse voiceRoomInfo,
    StageListResponse stageUsers,
    RoomUserListResponse audienceUsers
) {}
