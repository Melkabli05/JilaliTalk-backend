package com.jilali.roomcontext.infrastructure.dto.signin;

import com.jilali.roomcontext.infrastructure.dto.room.RoomLevelConfigResponse;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record RoomLevelBundleResponse(RoomLevelRewardResponse reward, RoomLevelConfigResponse config) {}
