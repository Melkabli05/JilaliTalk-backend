package com.jilali.room.dto;

import com.jilali.comment.dto.CommentListResponse;
import com.jilali.stage.dto.StageListResponse;
import com.jilali.user.dto.RoomUserListResponse;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Bundled result of the four calls needed to join a room, fanned out to upstream in parallel
 * from the BFF ({@link com.jilali.room.RoomJoinService}) so the browser makes a single
 * round-trip instead of four sequential/semi-parallel ones.
 *
 * @param voiceRoomInfo room metadata including decrypted RTC token for Agora (voice or live,
 *                      dispatched by {@code busiType} — see {@code RoomJoinService.joinBundle})
 * @param stageUsers    on-stage members
 * @param audienceUsers audience roster
 * @param comments      initial comment/chat history for the room
 */
@Serdeable
public record JoinBundleResponse(
    VoiceRoomInfoResponse voiceRoomInfo,
    StageListResponse stageUsers,
    RoomUserListResponse audienceUsers,
    CommentListResponse comments
) {}
