package com.jilali.signin.dto;

import com.jilali.platform.models.RewardItem;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record RoomLevelRewardResponse(
    List<RewardItem> items
) {}
