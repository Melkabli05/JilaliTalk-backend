package com.jilali.roomcontext.infrastructure.dto.room;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CategoryTopicTag(
        @JsonProperty("category_id") long categoryId,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("topic_id") @Nullable Long topicId,
        @JsonProperty("topic_name") @Nullable String topicName,
        @JsonProperty("bg_color") @Nullable String bgColor,
        @JsonProperty("font_color") @Nullable String fontColor) {
}
