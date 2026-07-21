package com.jilali.roomcontext.domain.valueobject;

import com.jilali.platform.models.RewardItem;

import java.util.List;

public record RoomLevel(int level, long exp, List<RewardItem> rewards) {

    public RoomLevel {
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
    }
}
