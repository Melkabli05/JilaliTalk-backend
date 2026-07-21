package com.jilali.roomcontext.infrastructure.dto.signin;

import com.jilali.platform.models.RewardItem;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record RoomLevelRewardResponse(List<RewardItem> items) {}
