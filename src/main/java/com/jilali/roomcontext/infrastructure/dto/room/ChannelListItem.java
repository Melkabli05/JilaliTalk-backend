package com.jilali.roomcontext.infrastructure.dto.room;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record ChannelListItem(
        Channel channel,
        @JsonProperty("host_user") HostUser hostUser,
        @Nullable List<RoomUser> users,
        @Nullable String token,
        @JsonProperty("background_url") @Nullable String backgroundUrl,
        @JsonProperty("category_topic_tag") @Nullable CategoryTopicTag categoryTopicTag) {
}
