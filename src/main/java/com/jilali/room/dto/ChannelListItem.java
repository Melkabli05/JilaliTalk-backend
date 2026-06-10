package com.jilali.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Room listing item shared across discovery endpoints (channel_list, recommend, end-page).
 * Fields are limited to what a frontend actually renders; we deliberately do not transcribe
 * every upstream field, since modeling unused data is just a different kind of bloat.
 */
@Serdeable
public record ChannelListItem(
        Channel channel,
        @JsonProperty("host_user") HostUser hostUser,
        @Nullable List<RoomUser> users,
        @Nullable String token,
        @JsonProperty("background_url") @Nullable String backgroundUrl,
        @JsonProperty("category_topic_tag") @Nullable CategoryTopicTag categoryTopicTag) {
}
