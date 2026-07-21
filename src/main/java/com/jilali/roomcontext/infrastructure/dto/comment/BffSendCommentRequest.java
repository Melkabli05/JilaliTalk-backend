package com.jilali.roomcontext.infrastructure.dto.comment;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record BffSendCommentRequest(
        @NotBlank String cname,
        int busiType,
        @NotBlank String nickname,
        @Nullable String headUrl,
        @Nullable String nationality,
        int role,
        @NotBlank String text,
        @Nullable ReplyInfo replyInfo) {

    @Serdeable
    public record ReplyInfo(String msgId, long fromId, String fromNickname, String text, String msgType) {}
}
