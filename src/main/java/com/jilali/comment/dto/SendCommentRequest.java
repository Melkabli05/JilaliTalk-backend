package com.jilali.comment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record SendCommentRequest(
        @NotBlank String cname,
        @JsonProperty("busi_type") int busiType,
        String nickname,
        @JsonProperty("head_url") @Nullable String headUrl,
        @Nullable String nationality,
        int role,
        Msg msg) {

    @Serdeable
    public record Msg(
            @JsonProperty("msg_type") String msgType,
            @JsonProperty("msg_model") String msgModel,
            @JsonProperty("send_time") String sendTime,
            @JsonProperty("from_nickname") @Nullable String fromNickname,
            String source,
            Text text,
            @JsonProperty("reply_info") @Nullable ReplyInfo replyInfo) {

        @Serdeable
        public record Text(String text) {}

        @Serdeable
        public record ReplyInfo(
                @JsonProperty("msg_id") String msgId,
                @JsonProperty("from_id") long fromId,
                @JsonProperty("from_nickname") String fromNickname,
                String text,
                @JsonProperty("msg_type") String msgType) {}
    }
}
