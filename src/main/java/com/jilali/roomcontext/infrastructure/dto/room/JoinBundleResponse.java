package com.jilali.roomcontext.infrastructure.dto.room;

import com.jilali.roomcontext.infrastructure.dto.comment.CommentListDto;
import com.jilali.roomcontext.infrastructure.dto.stage.StageListResponse;
import com.jilali.roomcontext.infrastructure.dto.user.RoomUserListResponse;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record JoinBundleResponse(
    VoiceRoomInfoResponse voiceRoomInfo,
    StageListResponse stageUsers,
    RoomUserListResponse audienceUsers,
    CommentListDto comments
) {}
